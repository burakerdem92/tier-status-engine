package com.turkishtechnology.tierstatus.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkishtechnology.tierstatus.domain.EventEnvelope;
import com.turkishtechnology.tierstatus.domain.EventType;
import com.turkishtechnology.tierstatus.domain.EventValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

@Component
public class EventParser {

    private final ObjectMapper objectMapper;

    public EventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EventEnvelope parse(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null || !root.isObject()) {
                throw new EventValidationException("Message must be a JSON object");
            }
            String eventId = requiredText(root, "eventId", 100);
            EventType eventType = EventType.valueOf(requiredText(root, "eventType"));
            Instant eventTime = Instant.parse(requiredText(root, "eventTime"));
            String source = requiredText(root, "source", 80);
            String memberId = requiredText(root, "memberId", 64);
            JsonNode payload = root.get("payload");
            if (payload == null || !payload.isObject()) {
                throw new EventValidationException("payload must be a JSON object");
            }
            validatePayload(eventType, payload);
            return new EventEnvelope(eventId, eventType, eventTime, source, memberId, payload);
        } catch (JsonProcessingException exception) {
            throw new EventValidationException("Message is not valid JSON", exception);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new EventValidationException("Envelope contains an invalid enum or date", exception);
        }
    }

    public String extractEventId(String rawJson) {
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            if (node == null || !node.isObject()) {
                return null;
            }
            JsonNode id = node.get("eventId");
            return id != null && id.isTextual() && !id.asText().isBlank()
                    && id.asText().length() <= 100 ? id.asText() : null;
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private void validatePayload(EventType eventType, JsonNode payload) {
        switch (eventType) {
            case FLIGHT_FLOWN -> {
                requiredText(payload, "operatingCarrier");
                requiredText(payload, "bookingClass");
                requiredDate(payload, "flightDate");
                requiredNonNegativeInteger(payload, "distanceMiles");
            }
            case PARTNER_ACTIVITY -> {
                requiredText(payload, "partnerCode");
                requiredText(payload, "partnerType");
                requiredText(payload, "currency");
                requiredDate(payload, "activityDate");
                requiredNonNegativeNumber(payload, "amount");
            }
            case MANUAL_ADJUSTMENT -> {
                requiredInteger(payload, "statusMiles");
                requiredDate(payload, "effectiveDate");
                requiredText(payload, "reason");
            }
            case ACCRUAL_REVERSAL -> {
                requiredText(payload, "originalEventId", 100);
                requiredText(payload, "reason");
            }
            case TIER_CHANGED -> throw new EventValidationException("TIER_CHANGED is an output event");
        }
    }

    private static void requiredDate(JsonNode node, String field) {
        String value = requiredText(node, field);
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new EventValidationException("Invalid date field: " + field, exception);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        return requiredText(node, field, 200);
    }

    private static String requiredText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new EventValidationException("Missing or invalid text field: " + field);
        }
        if (value.asText().length() > maxLength) {
            throw new EventValidationException("Text field is too long: " + field);
        }
        return value.asText();
    }

    private static void requiredInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new EventValidationException("Missing or invalid integer field: " + field);
        }
    }

    private static void requiredNonNegativeInteger(JsonNode node, String field) {
        requiredInteger(node, field);
        if (node.get(field).intValue() < 0) {
            throw new EventValidationException(field + " cannot be negative");
        }
    }

    private static void requiredNonNegativeNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new EventValidationException("Missing or invalid number field: " + field);
        }
        if (value.decimalValue().signum() < 0) {
            throw new EventValidationException(field + " cannot be negative");
        }
    }
}
