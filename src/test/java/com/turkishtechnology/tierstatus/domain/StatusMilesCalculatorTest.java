package com.turkishtechnology.tierstatus.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turkishtechnology.tierstatus.service.ReferenceRate;
import com.turkishtechnology.tierstatus.service.ReferenceRateProvider;
import com.turkishtechnology.tierstatus.service.StatusMilesCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StatusMilesCalculatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StatusMilesCalculator calculator = new StatusMilesCalculator(new FixedRates());

    @Test
    void appliesTkMinimumWhenPositiveAccrualIsBelowFiveHundred() throws Exception {
        EventEnvelope event = event(EventType.FLIGHT_FLOWN, """
                {"operatingCarrier":"TK","bookingClass":"P",
                 "flightDate":"2026-09-05","distanceMiles":100}
                """);

        CalculatedMovement result = calculator.calculate(event);

        assertThat(result.statusMiles()).isEqualTo(500);
    }

    @Test
    void doesNotApplyMinimumForPartnerCarrier() throws Exception {
        EventEnvelope event = event(EventType.FLIGHT_FLOWN, """
                {"operatingCarrier":"LH","bookingClass":"C",
                 "flightDate":"2026-09-05","distanceMiles":100}
                """);

        assertThat(calculator.calculate(event).statusMiles()).isEqualTo(150);
    }

    @Test
    void promotionalZeroRateDoesNotReceiveTkMinimum() throws Exception {
        EventEnvelope event = event(EventType.FLIGHT_FLOWN, """
                {"operatingCarrier":"TK","bookingClass":"X",
                 "flightDate":"2026-09-05","distanceMiles":100}
                """);
        assertThat(calculator.calculate(event).statusMiles()).isZero();
    }

    @Test
    void bankPartnerIsTrackedWithZeroStatusMiles() throws Exception {
        EventEnvelope event = event(EventType.PARTNER_ACTIVITY, """
                {"partnerCode":"DEMO_BANK","currency":"TRY",
                 "activityDate":"2026-08-28","amount":1000}
                """);
        assertThat(calculator.calculate(event).statusMiles()).isZero();
    }

    @Test
    void roundsPartnerAccrualHalfUp() throws Exception {
        EventEnvelope event = event(EventType.PARTNER_ACTIVITY, """
                {"partnerCode":"AURORA_HOTELS","currency":"USD",
                 "activityDate":"2026-08-28","amount":10.25}
                """);

        assertThat(calculator.calculate(event).statusMiles()).isEqualTo(21);
    }

    @Test
    void preservesNegativeManualAdjustment() throws Exception {
        EventEnvelope event = event(EventType.MANUAL_ADJUSTMENT, """
                {"statusMiles":-1200,"effectiveDate":"2026-09-01"}
                """);

        assertThat(calculator.calculate(event).statusMiles()).isEqualTo(-1200);
    }

    private EventEnvelope event(EventType type, String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        return new EventEnvelope("event-1", type, Instant.parse("2026-09-05T10:00:00Z"),
                "TEST", "TK123", node);
    }

    private static final class FixedRates implements ReferenceRateProvider {
        @Override
        public ReferenceRate flightRate(String operatingCarrier, String bookingClass,
                                        LocalDate activityDate) {
            BigDecimal rate = switch (bookingClass) {
                case "P" -> new BigDecimal("0.50");
                case "X" -> BigDecimal.ZERO;
                default -> new BigDecimal("1.50");
            };
            return new ReferenceRate(rate, "test-1");
        }

        @Override
        public ReferenceRate partnerRate(String partnerCode, String currency,
                                         LocalDate activityDate) {
            return new ReferenceRate("DEMO_BANK".equals(partnerCode)
                    ? BigDecimal.ZERO : new BigDecimal("2.00"), "test-1");
        }
    }
}
