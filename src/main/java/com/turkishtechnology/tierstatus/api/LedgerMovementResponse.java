package com.turkishtechnology.tierstatus.api;

import java.time.LocalDate;

public record LedgerMovementResponse(
        String sourceEventId,
        String eventType,
        String source,
        LocalDate activityDate,
        int statusMiles,
        int period,
        String rateVersion) {
}
