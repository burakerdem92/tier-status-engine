package com.turkishtechnology.tierstatus.persistence;

import com.turkishtechnology.tierstatus.domain.Tier;
import com.turkishtechnology.tierstatus.domain.TierChangeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tier_history")
public class TierHistoryEntity extends UuidEntity {

    @Column(name = "decision_key", nullable = false, unique = true, length = 300)
    private String decisionKey;
    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_tier", nullable = false, length = 40)
    private Tier previousTier;
    @Enumerated(EnumType.STRING)
    @Column(name = "new_tier", nullable = false, length = 40)
    private Tier newTier;
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 40)
    private TierChangeType changeType;
    @Column(name = "qualification_period", nullable = false)
    private int qualificationPeriod;
    @Column(name = "status_miles_in_period", nullable = false)
    private int statusMilesInPeriod;
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
    @Column(name = "valid_until")
    private LocalDate validUntil;
    @Column(name = "triggered_by_event_id", nullable = false, length = 100)
    private String triggeredByEventId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TierHistoryEntity() {
    }

    public TierHistoryEntity(String decisionKey, String memberId, Tier previousTier, Tier newTier,
                             TierChangeType changeType, int qualificationPeriod,
                             int statusMilesInPeriod, LocalDate effectiveDate, LocalDate validUntil,
                             String triggeredByEventId, Instant createdAt) {
        this.decisionKey = decisionKey;
        this.memberId = memberId;
        this.previousTier = previousTier;
        this.newTier = newTier;
        this.changeType = changeType;
        this.qualificationPeriod = qualificationPeriod;
        this.statusMilesInPeriod = statusMilesInPeriod;
        this.effectiveDate = effectiveDate;
        this.validUntil = validUntil;
        this.triggeredByEventId = triggeredByEventId;
        this.createdAt = createdAt;
    }

    public String getDecisionKey() { return decisionKey; }
    public String getMemberId() { return memberId; }
    public Tier getPreviousTier() { return previousTier; }
    public Tier getNewTier() { return newTier; }
    public TierChangeType getChangeType() { return changeType; }
    public int getQualificationPeriod() { return qualificationPeriod; }
    public int getStatusMilesInPeriod() { return statusMilesInPeriod; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public LocalDate getValidUntil() { return validUntil; }
    public String getTriggeredByEventId() { return triggeredByEventId; }
}
