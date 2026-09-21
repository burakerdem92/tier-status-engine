package com.turkishtechnology.tierstatus.domain;

import java.time.LocalDate;

public record CalculatedMovement(
        LocalDate activityDate,
        int statusMiles,
        String rateVersion) {
}
