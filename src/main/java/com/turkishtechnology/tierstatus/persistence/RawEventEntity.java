package com.turkishtechnology.tierstatus.persistence;

import com.turkishtechnology.tierstatus.domain.EventEnvelope;
import com.turkishtechnology.tierstatus.domain.EventType;
import com.turkishtechnology.tierstatus.domain.ProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raw_event")
public class RawEventEntity extends UuidEntity {

    @Column(name = "ingestion_key", nullable = false, unique = true, length = 200)
    private String ingestionKey;

    @Column(name = "event_id", unique = true, length = 100)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 40)
    private EventType eventType;

    @Column(name = "member_id", length = 64)
    private String memberId;

    @Column(name = "event_time")
    private Instant eventTime;

    @Column(length = 80)
    private String source;

    @Column(name = "raw_payload", nullable = false)
    private String rawPayload;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 40)
    private ProcessingStatus processingStatus;

    @Column(name = "validation_error", length = 2000)
    private String validationError;

    @Column(name = "processing_note", length = 2000)
    private String processingNote;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(name = "partition_no", nullable = false)
    private int partition;

    @Column(name = "offset_no", nullable = false)
    private long offset;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected RawEventEntity() {
    }

    public static RawEventEntity valid(String ingestionKey, String rawPayload, String payloadHash,
                                       String topic, int partition, long offset, Instant receivedAt,
                                       EventEnvelope envelope) {
        RawEventEntity entity = base(ingestionKey, rawPayload, payloadHash, topic, partition, offset, receivedAt);
        entity.eventId = envelope.eventId();
        entity.eventType = envelope.eventType();
        entity.memberId = envelope.memberId();
        entity.eventTime = envelope.eventTime();
        entity.source = envelope.source();
        entity.processingStatus = ProcessingStatus.READY;
        return entity;
    }

    public static RawEventEntity invalid(String ingestionKey, String eventId, String rawPayload,
                                         String payloadHash, String topic, int partition, long offset,
                                         Instant receivedAt, String validationError) {
        RawEventEntity entity = base(ingestionKey, rawPayload, payloadHash, topic, partition, offset, receivedAt);
        entity.eventId = eventId;
        entity.processingStatus = ProcessingStatus.INVALID;
        entity.validationError = validationError;
        return entity;
    }

    private static RawEventEntity base(String ingestionKey, String rawPayload, String payloadHash,
                                       String topic, int partition, long offset, Instant receivedAt) {
        RawEventEntity entity = new RawEventEntity();
        entity.ingestionKey = ingestionKey;
        entity.rawPayload = rawPayload;
        entity.payloadHash = payloadHash;
        entity.topic = topic;
        entity.partition = partition;
        entity.offset = offset;
        entity.receivedAt = receivedAt;
        return entity;
    }

    public void markProcessing() {
        processingStatus = ProcessingStatus.PROCESSING;
    }

    public void markWaitingForOriginal(String note) {
        processingStatus = ProcessingStatus.WAITING_FOR_ORIGINAL;
        processingNote = note;
    }

    public void markProcessed(Instant at) {
        processingStatus = ProcessingStatus.PROCESSED;
        processedAt = at;
        processingNote = null;
    }

    public void markFailed(String note) {
        processingStatus = ProcessingStatus.FAILED;
        processingNote = note;
    }

    public String getEventId() { return eventId; }
    public String getPayloadHash() { return payloadHash; }
    public String getRawPayload() { return rawPayload; }
    public ProcessingStatus getProcessingStatus() { return processingStatus; }
}
