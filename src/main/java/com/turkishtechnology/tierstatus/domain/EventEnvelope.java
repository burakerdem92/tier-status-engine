package com.turkishtechnology.tierstatus.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record EventEnvelope(
        String eventId,
        EventType eventType,
        Instant eventTime,
        String source,
        String memberId,
        JsonNode payload) {
}
