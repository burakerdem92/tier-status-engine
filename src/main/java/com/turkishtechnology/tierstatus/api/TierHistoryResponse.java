package com.turkishtechnology.tierstatus.api;

import com.turkishtechnology.tierstatus.domain.Tier;
import com.turkishtechnology.tierstatus.domain.TierChangeType;
import java.time.LocalDate;

public record TierHistoryResponse(
        LocalDate effectiveDate,
        Tier previousTier,
        Tier newTier,
        TierChangeType changeType,
        int qualificationPeriod,
        int statusMilesInPeriod,
        LocalDate validUntil,
        String triggeredByEventId) {
}
