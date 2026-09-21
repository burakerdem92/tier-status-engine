package com.turkishtechnology.tierstatus.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkishtechnology.tierstatus.persistence.MemberStatusEntity;
import com.turkishtechnology.tierstatus.persistence.MemberStatusRepository;
import com.turkishtechnology.tierstatus.persistence.OutboxEventEntity;
import com.turkishtechnology.tierstatus.persistence.OutboxEventRepository;
import com.turkishtechnology.tierstatus.persistence.TierHistoryEntity;
import com.turkishtechnology.tierstatus.persistence.TierHistoryRepository;
import com.turkishtechnology.tierstatus.service.TierDecisionService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TierDecisionServiceTest {

    private static final Instant NOW = Instant.parse("2027-01-01T00:00:00Z");
    private MemberStatusRepository statuses;
    private TierHistoryRepository history;
    private OutboxEventRepository outbox;
    private TierDecisionService service;

    @BeforeEach
    void setUp() {
        statuses = mock(MemberStatusRepository.class);
        history = mock(TierHistoryRepository.class);
        outbox = mock(OutboxEventRepository.class);
        service = new TierDecisionService(
                statuses, history, outbox, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), "tier-changed");
        when(history.existsByDecisionKey(anyString())).thenReturn(false);
    }

    @Test
    void downgradesOneLevelWhenRenewalThresholdIsNotMet() {
        MemberStatusEntity status = eliteStatus();
        when(statuses.findForUpdate("TK123")).thenReturn(Optional.of(status));

        service.evaluatePeriodEnd("TK123", LocalDate.of(2026, 12, 31), 29_999);

        assertThat(status.getTier()).isEqualTo(Tier.CLASSIC_PLUS);
        assertThat(status.getValidUntil()).isEqualTo(LocalDate.of(2027, 12, 31));

        ArgumentCaptor<TierHistoryEntity> historyCaptor =
                ArgumentCaptor.forClass(TierHistoryEntity.class);
        verify(history).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getChangeType()).isEqualTo(TierChangeType.DOWNGRADE);

        ArgumentCaptor<OutboxEventEntity> outboxCaptor =
                ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outbox).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getPayload())
                .contains("\"changeType\":\"DOWNGRADE\"")
                .contains("\"newTier\":\"CLASSIC_PLUS\"");
    }

    @Test
    void renewsCurrentTierWhenThresholdIsMet() {
        MemberStatusEntity status = eliteStatus();
        when(statuses.findForUpdate("TK123")).thenReturn(Optional.of(status));

        service.evaluatePeriodEnd("TK123", LocalDate.of(2026, 12, 31), 30_000);

        assertThat(status.getTier()).isEqualTo(Tier.ELITE);
        assertThat(status.getValidUntil()).isEqualTo(LocalDate.of(2027, 12, 31));

        ArgumentCaptor<TierHistoryEntity> historyCaptor =
                ArgumentCaptor.forClass(TierHistoryEntity.class);
        verify(history).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getChangeType()).isEqualTo(TierChangeType.RENEWAL);
    }

    @Test
    void lateFlightUpgradeUsesItsActivityYearAndNextYearExpiry() {
        MemberStatusEntity status = new MemberStatusEntity("TK123", NOW);
        when(statuses.findForUpdate("TK123")).thenReturn(Optional.of(status));
        EventEnvelope retro = new EventEnvelope("retro-1", EventType.FLIGHT_FLOWN,
                NOW, "RETRO_CLAIM", "TK123", null);

        service.evaluateAfterMovement(retro, 40_000, LocalDate.of(2026, 12, 31));

        assertThat(status.getTier()).isEqualTo(Tier.ELITE);
        assertThat(status.getValidUntil()).isEqualTo(LocalDate.of(2027, 12, 31));
        ArgumentCaptor<TierHistoryEntity> decision = ArgumentCaptor.forClass(TierHistoryEntity.class);
        verify(history).save(decision.capture());
        assertThat(decision.getValue().getQualificationPeriod()).isEqualTo(2026);
    }

    @Test
    void currentPeriodCancellationRevokesUpgradeWhenMilesFallBelowThreshold() {
        LocalDate today = LocalDate.of(2026, 9, 21);
        TierDecisionService currentYearService = new TierDecisionService(
                statuses, history, outbox, new ObjectMapper(),
                Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                "tier-changed");
        MemberStatusEntity status = new MemberStatusEntity("TK123", NOW);
        status.changeTo(Tier.CLASSIC_PLUS, LocalDate.of(2027, 12, 31), NOW);
        when(statuses.findForUpdate("TK123")).thenReturn(Optional.of(status));
        when(history.findPriorValidChanges(org.mockito.ArgumentMatchers.eq("TK123"),
                org.mockito.ArgumentMatchers.eq(2026), org.mockito.ArgumentMatchers.eq(today),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

        EventEnvelope reversal = new EventEnvelope("cancel-1", EventType.ACCRUAL_REVERSAL,
                NOW, "REVENUE_ACCOUNTING", "TK123", null);
        currentYearService.evaluateAfterMovement(reversal, 23_000, today);

        assertThat(status.getTier()).isEqualTo(Tier.CLASSIC);
        assertThat(status.getValidUntil()).isNull();
        ArgumentCaptor<TierHistoryEntity> decision = ArgumentCaptor.forClass(TierHistoryEntity.class);
        verify(history).save(decision.capture());
        assertThat(decision.getValue().getChangeType()).isEqualTo(TierChangeType.DOWNGRADE);
        assertThat(decision.getValue().getTriggeredByEventId()).isEqualTo("cancel-1");
        ArgumentCaptor<OutboxEventEntity> publication = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outbox).save(publication.capture());
        assertThat(publication.getValue().getPayload()).contains("\"newTier\":\"CLASSIC\"");
    }

    @Test
    void cancellationKeepsTheTierPreviouslyEarnedInAnotherYear() {
        LocalDate today = LocalDate.of(2026, 9, 21);
        TierDecisionService currentYearService = new TierDecisionService(
                statuses, history, outbox, new ObjectMapper(),
                Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC),
                "tier-changed");
        MemberStatusEntity status = new MemberStatusEntity("TK123", NOW);
        status.changeTo(Tier.ELITE, LocalDate.of(2027, 12, 31), NOW);
        when(statuses.findForUpdate("TK123")).thenReturn(Optional.of(status));
        TierHistoryEntity prior = new TierHistoryEntity("prior", "TK123", Tier.CLASSIC,
                Tier.CLASSIC_PLUS, TierChangeType.UPGRADE, 2025, 30_000,
                LocalDate.of(2025, 9, 1), LocalDate.of(2026, 12, 31), "earlier", NOW);
        when(history.findPriorValidChanges(org.mockito.ArgumentMatchers.eq("TK123"),
                org.mockito.ArgumentMatchers.eq(2026), org.mockito.ArgumentMatchers.eq(today),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(prior));

        EventEnvelope reversal = new EventEnvelope("cancel-2", EventType.ACCRUAL_REVERSAL,
                NOW, "REVENUE_ACCOUNTING", "TK123", null);
        currentYearService.evaluateAfterMovement(reversal, 20_000, today);

        assertThat(status.getTier()).isEqualTo(Tier.CLASSIC_PLUS);
        assertThat(status.getValidUntil()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void closedPeriodCancellationLeavesPreviousTierDecisionIntact() {
        MemberStatusEntity status = eliteStatus();
        when(statuses.findForUpdate("TK123")).thenReturn(Optional.of(status));
        EventEnvelope reversal = new EventEnvelope("cancel-old", EventType.ACCRUAL_REVERSAL,
                NOW, "REVENUE_ACCOUNTING", "TK123", null);

        service.evaluateAfterMovement(reversal, 0, LocalDate.of(2026, 9, 21));

        assertThat(status.getTier()).isEqualTo(Tier.ELITE);
        verify(history, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static MemberStatusEntity eliteStatus() {
        MemberStatusEntity status = new MemberStatusEntity("TK123", NOW.minusSeconds(60));
        status.changeTo(Tier.ELITE, LocalDate.of(2026, 12, 31), NOW.minusSeconds(30));
        return status;
    }
}
