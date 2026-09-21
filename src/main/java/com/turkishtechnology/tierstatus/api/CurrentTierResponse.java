package com.turkishtechnology.tierstatus.api;

import com.turkishtechnology.tierstatus.domain.Tier;
import java.time.Instant;
import java.time.LocalDate;

public record CurrentTierResponse(
        String memberId,
        Tier tier,
        LocalDate validUntil,
        int qualificationPeriod,
        int statusMilesInPeriod,
        Tier nextTier,
        Integer milesToNextTier,
        Integer renewalThreshold,
        Integer milesToRenew,
        Instant asOf) {
}
