package com.turkishtechnology.tierstatus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.turkishtechnology.tierstatus.domain.CalculatedMovement;
import com.turkishtechnology.tierstatus.domain.EventEnvelope;
import com.turkishtechnology.tierstatus.domain.EventValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class StatusMilesCalculator {

    private final ReferenceRateProvider rateProvider;

    public StatusMilesCalculator(ReferenceRateProvider rateProvider) {
        this.rateProvider = rateProvider;
    }

    public CalculatedMovement calculate(EventEnvelope event) {
        return switch (event.eventType()) {
            case FLIGHT_FLOWN -> flight(event.payload());
            case PARTNER_ACTIVITY -> partner(event.payload());
            case MANUAL_ADJUSTMENT -> manual(event.payload());
            case ACCRUAL_REVERSAL, TIER_CHANGED -> throw new EventValidationException(
                    "Event type is not calculated directly: " + event.eventType());
        };
    }

    private CalculatedMovement flight(JsonNode payload) {
        String carrier = payload.path("operatingCarrier").asText();
        String bookingClass = payload.path("bookingClass").asText();
        LocalDate date = LocalDate.parse(payload.path("flightDate").asText());
        int distance = payload.path("distanceMiles").asInt();
        if (distance < 0) {
            throw new EventValidationException("distanceMiles cannot be negative");
        }

        ReferenceRate rate = rateProvider.flightRate(carrier, bookingClass, date);
        int result = round(BigDecimal.valueOf(distance).multiply(rate.value()));
        if ("TK".equals(carrier) && rate.value().signum() > 0 && result < 500) {
            result = 500;
        }
        return new CalculatedMovement(date, result, rate.version());
    }

    private CalculatedMovement partner(JsonNode payload) {
        String partnerCode = payload.path("partnerCode").asText();
        String currency = payload.path("currency").asText();
        LocalDate date = LocalDate.parse(payload.path("activityDate").asText());
        BigDecimal amount = payload.path("amount").decimalValue();
        if (amount.signum() < 0) {
            throw new EventValidationException("amount cannot be negative");
        }

        ReferenceRate rate = rateProvider.partnerRate(partnerCode, currency, date);
        return new CalculatedMovement(date, round(amount.multiply(rate.value())), rate.version());
    }

    private CalculatedMovement manual(JsonNode payload) {
        LocalDate date = LocalDate.parse(payload.path("effectiveDate").asText());
        return new CalculatedMovement(date, payload.path("statusMiles").asInt(), "MANUAL");
    }

    static int round(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
