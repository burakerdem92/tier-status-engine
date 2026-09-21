package com.turkishtechnology.tierstatus.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkishtechnology.tierstatus.domain.EventEnvelope;
import com.turkishtechnology.tierstatus.domain.EventValidationException;
import com.turkishtechnology.tierstatus.persistence.OutboxEventEntity;
import com.turkishtechnology.tierstatus.persistence.OutboxEventRepository;
import com.turkishtechnology.tierstatus.persistence.RawEventEntity;
import com.turkishtechnology.tierstatus.persistence.RawEventRepository;
import com.turkishtechnology.tierstatus.service.HashingService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionService {

    private final RawEventRepository rawEvents;
    private final OutboxEventRepository outboxEvents;
    private final EventParser parser;
    private final HashingService hashing;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String memberTopic;
    private final String dltTopic;

    public IngestionService(RawEventRepository rawEvents,
                            OutboxEventRepository outboxEvents,
                            EventParser parser,
                            HashingService hashing,
                            ObjectMapper objectMapper,
                            Clock clock,
                            @Value("${tier.kafka.member-topic}") String memberTopic,
                            @Value("${tier.kafka.dlt-topic}") String dltTopic) {
        this.rawEvents = rawEvents;
        this.outboxEvents = outboxEvents;
        this.parser = parser;
        this.hashing = hashing;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.memberTopic = memberTopic;
        this.dltTopic = dltTopic;
    }

    @Transactional
    public void ingest(String topic, int partition, long offset, String rawJson) {
        String ingestionKey = "%s:%d:%d".formatted(topic, partition, offset);
        if (rawEvents.existsByIngestionKey(ingestionKey)) {
            return;
        }

        Instant now = clock.instant();
        if (rawJson == null) {
            // A Kafka tombstone has no JSON bytes; retain its delivery metadata for audit.
            rawEvents.save(RawEventEntity.invalid(
                    ingestionKey, null, "", hashing.sha256(""), topic, partition, offset, now,
                    "Kafka record value is null"));
            enqueueDlt(null, ingestionKey, "", "Kafka record value is null", now);
            return;
        }
        String hash = hashing.sha256(rawJson);
        try {
            EventEnvelope event = parser.parse(rawJson);
            var existing = rawEvents.findByEventId(event.eventId());
            if (existing.isPresent()) {
                if (!existing.get().getPayloadHash().equals(hash)) {
                    rawEvents.save(RawEventEntity.invalid(
                            ingestionKey, null, rawJson, hash, topic, partition, offset, now,
                            "eventId was reused with a different payload"));
                    enqueueDlt(event.eventId(), ingestionKey, rawJson,
                            "eventId was reused with a different payload", now);
                }
                return;
            }

            rawEvents.save(RawEventEntity.valid(
                    ingestionKey, rawJson, hash, topic, partition, offset, now, event));
            outboxEvents.save(new OutboxEventEntity(
                    memberTopic, event.memberId(), event.eventType().name(), rawJson, now));
        } catch (EventValidationException exception) {
            String eventId = parser.extractEventId(rawJson);
            if (eventId != null) {
                var existing = rawEvents.findByEventId(eventId);
                if (existing.isPresent()) {
                    if (existing.get().getPayloadHash().equals(hash)) {
                        return;
                    }
                    // Keep the conflicting delivery by ingestion key without violating event_id UNIQUE.
                    rawEvents.save(RawEventEntity.invalid(
                            ingestionKey, null, rawJson, hash, topic, partition, offset, now,
                            "eventId was reused with a different payload: "
                                    + truncate(exception.getMessage(), 1900)));
                    enqueueDlt(eventId, ingestionKey, rawJson,
                            "eventId was reused with a different payload: "
                                    + exception.getMessage(), now);
                    return;
                }
            }
            rawEvents.save(RawEventEntity.invalid(
                    ingestionKey, eventId, rawJson, hash, topic, partition, offset, now,
                    truncate(exception.getMessage(), 2000)));
            enqueueDlt(eventId, ingestionKey, rawJson, exception.getMessage(), now);
        }
    }

    private void enqueueDlt(String eventId, String ingestionKey, String rawJson,
                            String error, Instant now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("ingestionKey", ingestionKey);
        payload.put("error", error);
        payload.put("rawMessage", rawJson);
        try {
            outboxEvents.save(new OutboxEventEntity(
                    dltTopic, eventId == null ? ingestionKey : eventId,
                    "INVALID_EVENT", objectMapper.writeValueAsString(payload), now));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize DLT message", exception);
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
