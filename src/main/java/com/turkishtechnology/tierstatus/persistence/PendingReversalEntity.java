package com.turkishtechnology.tierstatus.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "pending_reversal")
public class PendingReversalEntity {

    @Id
    @Column(name = "original_event_id", length = 100)
    private String originalEventId;

    @Column(name = "reversal_event_id", nullable = false, unique = true, length = 100)
    private String reversalEventId;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected PendingReversalEntity() {
    }

    public PendingReversalEntity(String originalEventId, String reversalEventId,
                                 String memberId, Instant receivedAt) {
        this.originalEventId = originalEventId;
        this.reversalEventId = reversalEventId;
        this.memberId = memberId;
        this.receivedAt = receivedAt;
    }

    public String getOriginalEventId() { return originalEventId; }
    public String getReversalEventId() { return reversalEventId; }
    public String getMemberId() { return memberId; }
}
