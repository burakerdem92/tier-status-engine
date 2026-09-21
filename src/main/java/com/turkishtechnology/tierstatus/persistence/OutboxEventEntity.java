package com.turkishtechnology.tierstatus.persistence;

import com.turkishtechnology.tierstatus.domain.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity extends UuidEntity {

    @Column(nullable = false, length = 200)
    private String topic;
    @Column(name = "message_key", nullable = false, length = 200)
    private String messageKey;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(nullable = false)
    private String payload;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutboxStatus status;
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    @Column(name = "claimed_at")
    private Instant claimedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected OutboxEventEntity() {
    }

    public OutboxEventEntity(String topic, String messageKey, String eventType,
                             String payload, Instant createdAt) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.status = OutboxStatus.PENDING;
    }

    public void claim(Instant at) {
        status = OutboxStatus.PROCESSING;
        claimedAt = at;
    }

    public void markPublished(Instant at) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = at;
        claimedAt = null;
    }

    public void markFailed(Instant nextAttemptAt) {
        attempts++;
        status = OutboxStatus.PENDING;
        claimedAt = null;
        this.nextAttemptAt = nextAttemptAt;
    }

    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public int getAttempts() { return attempts; }
}
