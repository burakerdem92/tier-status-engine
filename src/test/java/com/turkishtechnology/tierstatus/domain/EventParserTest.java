package com.turkishtechnology.tierstatus.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkishtechnology.tierstatus.ingestion.EventParser;
import org.junit.jupiter.api.Test;

class EventParserTest {

    private final EventParser parser = new EventParser(new ObjectMapper());

    @Test
    void rejectsEmptyBodyAndDoesNotExtractOversizedEventId() {
        assertThatThrownBy(() -> parser.parse("  "))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("JSON object");
        assertThat(parser.extractEventId("  ")).isNull();
        assertThat(parser.extractEventId("{\"eventId\":\"" + "x".repeat(101) + "\"}"))
                .isNull();
    }

    @Test
    void rejectsMissingPayloadField() {
        String event = """
                {
                  "eventId":"e1",
                  "eventType":"FLIGHT_FLOWN",
                  "eventTime":"2026-09-05T14:32:10Z",
                  "source":"DCS",
                  "memberId":"TK123",
                  "payload":{"operatingCarrier":"TK"}
                }
                """;

        assertThatThrownBy(() -> parser.parse(event))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("bookingClass");
    }

    @Test
    void rejectsNumericFieldEncodedAsText() {
        String event = """
                {
                  "eventId":"e2",
                  "eventType":"FLIGHT_FLOWN",
                  "eventTime":"2026-09-05T14:32:10Z",
                  "source":"DCS",
                  "memberId":"TK123",
                  "payload":{
                    "operatingCarrier":"TK",
                    "bookingClass":"J",
                    "flightDate":"2026-09-05",
                    "distanceMiles":"1552"
                  }
                }
                """;

        assertThatThrownBy(() -> parser.parse(event))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("distanceMiles");
    }

    @Test
    void rejectsInvalidActivityDateBeforeProcessing() {
        String event = """
                {
                  "eventId":"e3",
                  "eventType":"MANUAL_ADJUSTMENT",
                  "eventTime":"2026-09-05T14:32:10Z",
                  "source":"CALL_CENTER",
                  "memberId":"TK123",
                  "payload":{
                    "statusMiles":1200,
                    "effectiveDate":"2026-02-30",
                    "reason":"GOODWILL"
                  }
                }
                """;

        assertThatThrownBy(() -> parser.parse(event))
                .isInstanceOf(EventValidationException.class)
                .hasMessageContaining("effectiveDate");
    }
}
