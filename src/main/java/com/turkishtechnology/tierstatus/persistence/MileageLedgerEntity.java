package com.turkishtechnology.tierstatus.persistence;

import com.turkishtechnology.tierstatus.domain.EventEnvelope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "mileage_ledger")
public class MileageLedgerEntity extends UuidEntity {

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "source_event_id", nullable = false, unique = true, length = 100)
    private String sourceEventId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, length = 80)
    private String source;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "status_miles", nullable = false)
    private int statusMiles;

    @Column(name = "original_movement_id", unique = true, length = 36)
    private String originalMovementId;

    @Column(name = "rate_version", length = 80)
    private String rateVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MileageLedgerEntity() {
    }

    public static MileageLedgerEntity from(EventEnvelope event, LocalDate activityDate,
                                           int statusMiles, String rateVersion, Instant createdAt) {
        MileageLedgerEntity entity = new MileageLedgerEntity();
        entity.memberId = event.memberId();
        entity.sourceEventId = event.eventId();
        entity.eventType = event.eventType().name();
        entity.source = event.source();
        entity.activityDate = activityDate;
        entity.periodYear = activityDate.getYear();
        entity.statusMiles = statusMiles;
        entity.rateVersion = rateVersion;
        entity.createdAt = createdAt;
        return entity;
    }

    public static MileageLedgerEntity reversal(EventEnvelope event, MileageLedgerEntity original,
                                                Instant createdAt) {
        MileageLedgerEntity entity = from(event, original.activityDate,
                Math.negateExact(original.statusMiles), original.rateVersion, createdAt);
        entity.originalMovementId = original.getId();
        return entity;
    }

    public String getMemberId() { return memberId; }
    public String getSourceEventId() { return sourceEventId; }
    public String getEventType() { return eventType; }
    public String getSource() { return source; }
    public LocalDate getActivityDate() { return activityDate; }
    public int getPeriodYear() { return periodYear; }
    public int getStatusMiles() { return statusMiles; }
    public String getRateVersion() { return rateVersion; }
}
