package com.turkishtechnology.tierstatus.service;

import com.turkishtechnology.tierstatus.domain.CalculatedMovement;
import com.turkishtechnology.tierstatus.domain.EventEnvelope;
import com.turkishtechnology.tierstatus.domain.EventType;
import com.turkishtechnology.tierstatus.domain.ProcessingStatus;
import com.turkishtechnology.tierstatus.ingestion.EventParser;
import com.turkishtechnology.tierstatus.persistence.MemberPeriodEntity;
import com.turkishtechnology.tierstatus.persistence.MemberPeriodRepository;
import com.turkishtechnology.tierstatus.persistence.MileageLedgerEntity;
import com.turkishtechnology.tierstatus.persistence.MileageLedgerRepository;
import com.turkishtechnology.tierstatus.persistence.PendingReversalEntity;
import com.turkishtechnology.tierstatus.persistence.PendingReversalRepository;
import com.turkishtechnology.tierstatus.persistence.RawEventEntity;
import com.turkishtechnology.tierstatus.persistence.RawEventRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TierProcessingService {

    private static final Logger log = LoggerFactory.getLogger(TierProcessingService.class);
    private final EventParser parser;
    private final StatusMilesCalculator calculator;
    private final RawEventRepository rawEvents;
    private final MileageLedgerRepository ledger;
    private final MemberPeriodRepository memberPeriods;
    private final PendingReversalRepository pendingReversals;
    private final TierDecisionService tierDecisionService;
    private final Clock clock;

    public TierProcessingService(EventParser parser,
                                 StatusMilesCalculator calculator,
                                 RawEventRepository rawEvents,
                                 MileageLedgerRepository ledger,
                                 MemberPeriodRepository memberPeriods,
                                 PendingReversalRepository pendingReversals,
                                 TierDecisionService tierDecisionService,
                                 Clock clock) {
        this.parser = parser;
        this.calculator = calculator;
        this.rawEvents = rawEvents;
        this.ledger = ledger;
        this.memberPeriods = memberPeriods;
        this.pendingReversals = pendingReversals;
        this.tierDecisionService = tierDecisionService;
        this.clock = clock;
    }

    @Transactional
    public void process(String rawJson) {
        EventEnvelope event = parser.parse(rawJson);
        RawEventEntity rawEvent = rawEvents.findByEventId(event.eventId())
                .orElseThrow(() -> new IllegalStateException(
                        "Raw event must exist before processing: " + event.eventId()));

        if (rawEvent.getProcessingStatus() == ProcessingStatus.PROCESSED
                || ledger.existsBySourceEventId(event.eventId())) {
            rawEvent.markProcessed(clock.instant());
            return;
        }

        rawEvent.markProcessing();
        if (event.eventType() == EventType.ACCRUAL_REVERSAL) {
            processReversal(event, rawEvent);
            return;
        }

        CalculatedMovement result = calculator.calculate(event);
        MileageLedgerEntity movement = MileageLedgerEntity.from(
                event, result.activityDate(), result.statusMiles(), result.rateVersion(), clock.instant());
        int periodTotal = applyMovement(movement);
        rawEvent.markProcessed(clock.instant());
        // Evaluate the final total once; an already-known reversal must not grant a transient tier.
        periodTotal = applyPendingReversal(event, movement, periodTotal);
        tierDecisionService.evaluateAfterMovement(event, periodTotal, movement.getActivityDate());
    }

    private void processReversal(EventEnvelope event, RawEventEntity rawEvent) {
        String originalEventId = event.payload().path("originalEventId").asText();
        MileageLedgerEntity original = ledger.findBySourceEventId(originalEventId).orElse(null);
        if (original == null) {
            pendingReversals.save(new PendingReversalEntity(
                    originalEventId, event.eventId(), event.memberId(), clock.instant()));
            rawEvent.markWaitingForOriginal("Waiting for original event " + originalEventId);
            return;
        }
        int periodTotal = applyReversal(event, rawEvent, original);
        tierDecisionService.evaluateAfterMovement(event, periodTotal, original.getActivityDate());
    }

    private int applyPendingReversal(EventEnvelope originalEvent, MileageLedgerEntity originalMovement,
                                     int periodTotal) {
        return pendingReversals.findById(originalEvent.eventId()).map(pending -> {
            RawEventEntity reversalRaw = rawEvents.findByEventId(pending.getReversalEventId())
                    .orElseThrow(() -> new IllegalStateException("Pending reversal raw event is missing"));
            EventEnvelope reversal = parser.parse(reversalRaw.getRawPayload());
            int adjustedTotal = applyReversal(reversal, reversalRaw, originalMovement);
            pendingReversals.delete(pending);
            return adjustedTotal;
        }).orElse(periodTotal);
    }

    private int applyReversal(EventEnvelope reversal, RawEventEntity rawEvent,
                               MileageLedgerEntity original) {
        if (!reversal.memberId().equals(original.getMemberId())) {
            throw new com.turkishtechnology.tierstatus.domain.EventValidationException(
                    "Reversal memberId does not match the original event");
        }
        if ("ACCRUAL_REVERSAL".equals(original.getEventType())) {
            throw new com.turkishtechnology.tierstatus.domain.EventValidationException(
                    "A reversal cannot target another reversal");
        }
        if (ledger.existsByOriginalMovementId(original.getId())) {
            throw new com.turkishtechnology.tierstatus.domain.EventValidationException(
                    "Original event has already been reversed: " + original.getSourceEventId());
        }
        MileageLedgerEntity movement = MileageLedgerEntity.reversal(
                reversal, original, clock.instant());
        int periodTotal = applyMovement(movement);
        rawEvent.markProcessed(clock.instant());
        return periodTotal;
    }

    private int applyMovement(MileageLedgerEntity movement) {
        ledger.save(movement);
        Instant now = clock.instant();
        MemberPeriodEntity period = memberPeriods.findForUpdate(
                        movement.getMemberId(), movement.getPeriodYear())
                .orElseGet(() -> memberPeriods.save(new MemberPeriodEntity(
                        movement.getMemberId(), movement.getPeriodYear(), now)));
        period.addMiles(movement.getStatusMiles(), now);

        if (movement.getActivityDate().getYear() < java.time.LocalDate.now(clock).getYear()
                && movement.getStatusMiles() < 0) {
            log.info("Closed-period negative correction recorded for member {} and period {}. " +
                            "Past benefits are preserved by policy.",
                    movement.getMemberId(), movement.getPeriodYear());
        }
        return period.getTotalStatusMiles();
    }
}
