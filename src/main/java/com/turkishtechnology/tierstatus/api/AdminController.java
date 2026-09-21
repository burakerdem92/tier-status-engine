package com.turkishtechnology.tierstatus.api;

import com.turkishtechnology.tierstatus.service.PeriodEndService;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("!prod")
@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnProperty(name = "tier.roles.api-enabled", havingValue = "true", matchIfMissing = true)
public class AdminController {

    private final PeriodEndService periodEndService;

    public AdminController(PeriodEndService periodEndService) {
        this.periodEndService = periodEndService;
    }

    @PostMapping("/period-end")
    Map<String, Object> evaluatePeriodEnd(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Map.of("periodEnd", date, "evaluatedMembers", periodEndService.evaluate(date));
    }
}
