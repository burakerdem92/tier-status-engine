package com.turkishtechnology.tierstatus.api;

import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/{memberId}")
@ConditionalOnProperty(name = "tier.roles.api-enabled", havingValue = "true", matchIfMissing = true)
public class TierQueryController {

    private final TierQueryService queryService;

    public TierQueryController(TierQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/tier")
    CurrentTierResponse currentTier(@PathVariable String memberId) {
        return queryService.currentTier(memberId);
    }

    @GetMapping("/tier-history")
    List<TierHistoryResponse> history(
            @PathVariable String memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") int limit) {
        requireRange(from, to);
        return queryService.history(memberId, from, to, limit);
    }

    @GetMapping("/mileage-ledger")
    List<LedgerMovementResponse> ledger(
            @PathVariable String memberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "100") int limit) {
        requireRange(from, to);
        return queryService.movements(memberId, from, to, limit);
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from cannot be after to");
        }
    }
}
