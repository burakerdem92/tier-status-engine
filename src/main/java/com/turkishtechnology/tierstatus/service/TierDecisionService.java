package com.turkishtechnology.tierstatus.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkishtechnology.tierstatus.domain.EventEnvelope;
import com.turkishtechnology.tierstatus.domain.EventType;
import com.turkishtechnology.tierstatus.domain.Tier;
import com.turkishtechnology.tierstatus.domain.TierChangeType;
import com.turkishtechnology.tierstatus.persistence.MemberStatusEntity;
import com.turkishtechnology.tierstatus.persistence.MemberStatusRepository;
import com.turkishtechnology.tierstatus.persistence.OutboxEventEntity;
import com.turkishtechnology.tierstatus.persistence.OutboxEventRepository;
import com.turkishtechnology.tierstatus.persistence.TierHistoryEntity;
import com.turkishtechnology.tierstatus.persistence.TierHistoryRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class TierDecisionService {

    private final MemberStatusRepository memberStatuses;
    private final TierHistoryRepository tierHistory;
    private final OutboxEventRepository outboxEvents;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String changedTopic;

    public TierDecisionService(MemberStatusRepository memberStatuses,
                               TierHistoryRepository tierHistory,
                               OutboxEventRepository outboxEvents,
                               ObjectMapper objectMapper,
                               Clock clock,
                               @Value("${tier.kafka.changed-topic}") String changedTopic) {
        this.memberStatuses = memberStatuses;
        this.tierHistory = tierHistory;
        this.outboxEvents = outboxEvents;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.changedTopic = changedTopic;
    }

    public void evaluateAfterMovement(EventEnvelope event, int periodTotal, LocalDate activityDate) {
        MemberStatusEntity status = memberStatuses.findForUpdate(event.memberId())
                .orElseGet(() -> memberStatuses.save(
                        new MemberStatusEntity(event.memberId(), clock.instant())));

        Tier qualified = Tier.highestQualified(periodTotal);
        LocalDate earnedUntil = LocalDate.of(activityDate.getYear() + 1, 12, 31);
        LocalDate today = LocalDate.now(clock);
        if (earnedUntil.isBefore(today)) {
            return;
        }

        if (qualified.rank() > status.getTier().rank()) {
            recordChange(status, qualified, TierChangeType.UPGRADE, activityDate.getYear(),
                    periodTotal, activityDate, earnedUntil, event.eventId());
            return;
        }

        if (qualified == status.getTier() && qualified != Tier.CLASSIC
                && (status.getValidUntil() == null || earnedUntil.isAfter(status.getValidUntil()))) {
            recordChange(status, status.getTier(), TierChangeType.RENEWAL, activityDate.getYear(),
                    periodTotal, activityDate, earnedUntil, event.eventId());
        }

        if (event.eventType() == EventType.ACCRUAL_REVERSAL
                && activityDate.getYear() == today.getYear()
                && qualified.rank() < status.getTier().rank()) {
            correctCurrentPeriodCancellation(status, event, qualified, periodTotal, today, earnedUntil);
        }
    }

    private void correctCurrentPeriodCancellation(MemberStatusEntity status, EventEnvelope reversal,
                                                  Tier qualified, int periodTotal,
                                                  LocalDate today, LocalDate earnedUntil) {
        // A cancellation in the active period can revoke this period's upgrade. Preserve a
        // still-valid entitlement earned in an earlier period, including its original expiry.
        TierHistoryEntity prior = tierHistory.findPriorValidChanges(
                        reversal.memberId(), today.getYear(), today, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);
        Tier priorTier = prior == null ? Tier.CLASSIC : prior.getNewTier();
        Tier correctedTier = qualified.rank() >= priorTier.rank() ? qualified : priorTier;
        if (correctedTier.rank() >= status.getTier().rank()) {
            return;
        }
        LocalDate correctedExpiry = correctedTier == Tier.CLASSIC ? null
                : correctedTier == priorTier && priorTier.rank() > qualified.rank()
                ? prior.getValidUntil() : earnedUntil;
        recordChange(status, correctedTier, TierChangeType.DOWNGRADE, today.getYear(),
                periodTotal, today, correctedExpiry, reversal.eventId());
    }

    public void evaluatePeriodEnd(String memberId, LocalDate periodEnd, int periodTotal) {
        MemberStatusEntity status = memberStatuses.findForUpdate(memberId).orElse(null);
        if (status == null || status.getTier() == Tier.CLASSIC
                || !periodEnd.equals(status.getValidUntil())) {
            return;
        }

        LocalDate effectiveDate = periodEnd.plusDays(1);
        String triggerId = deterministicId("PERIOD_END:" + memberId + ":" + periodEnd.getYear());
        if (periodTotal >= status.getTier().renewalThreshold()) {
            recordChange(status, status.getTier(), TierChangeType.RENEWAL, periodEnd.getYear(),
                    periodTotal, effectiveDate, periodEnd.plusYears(1), triggerId);
        } else {
            Tier newTier = status.getTier().lower();
            LocalDate validUntil = newTier == Tier.CLASSIC ? null : periodEnd.plusYears(1);
            recordChange(status, newTier, TierChangeType.DOWNGRADE, periodEnd.getYear(),
                    periodTotal, effectiveDate, validUntil, triggerId);
        }
    }

    private void recordChange(MemberStatusEntity status, Tier newTier, TierChangeType changeType,
                              int qualificationPeriod, int periodTotal, LocalDate effectiveDate,
                              LocalDate validUntil, String triggeredByEventId) {
        Tier previousTier = status.getTier();
        String decisionKey = String.join(":",
                status.getMemberId(), changeType.name(), newTier.name(),
                effectiveDate.toString(), triggeredByEventId);
        if (tierHistory.existsByDecisionKey(decisionKey)) {
            return;
        }

        Instant now = clock.instant();
        status.changeTo(newTier, validUntil, now);
        tierHistory.save(new TierHistoryEntity(
                decisionKey, status.getMemberId(), previousTier, newTier, changeType,
                qualificationPeriod, periodTotal, effectiveDate, validUntil,
                triggeredByEventId, now));
        outboxEvents.save(new OutboxEventEntity(
                changedTopic, status.getMemberId(), "TIER_CHANGED",
                tierChangedPayload(decisionKey, status.getMemberId(), previousTier, newTier,
                        changeType, qualificationPeriod, periodTotal, effectiveDate,
                        validUntil, triggeredByEventId, now), now));
    }

    private String tierChangedPayload(String decisionKey, String memberId, Tier previousTier,
                                      Tier newTier, TierChangeType changeType,
                                      int qualificationPeriod, int periodTotal,
                                      LocalDate effectiveDate, LocalDate validUntil,
                                      String triggeredByEventId, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previousTier", previousTier.name());
        payload.put("newTier", newTier.name());
        payload.put("changeType", changeType.name());
        payload.put("qualificationPeriod", Integer.toString(qualificationPeriod));
        payload.put("statusMilesInPeriod", periodTotal);
        payload.put("effectiveDate", effectiveDate.toString());
        payload.put("validUntil", validUntil == null ? null : validUntil.toString());
        payload.put("triggeredByEventId", triggeredByEventId);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", deterministicId(decisionKey));
        envelope.put("eventType", "TIER_CHANGED");
        envelope.put("eventTime", now.toString());
        envelope.put("source", "TIER_STATUS_ENGINE");
        envelope.put("memberId", memberId);
        envelope.put("payload", payload);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize TIER_CHANGED", exception);
        }
    }

    static String deterministicId(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
