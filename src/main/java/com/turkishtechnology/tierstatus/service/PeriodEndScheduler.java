package com.turkishtechnology.tierstatus.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tier.roles.period-end-enabled", havingValue = "true", matchIfMissing = true)
public class PeriodEndScheduler {

    private final PeriodEndService periodEndService;
    private final Clock clock;

    public PeriodEndScheduler(PeriodEndService periodEndService, Clock clock) {
        this.periodEndService = periodEndService;
        this.clock = clock;
    }

    @Scheduled(cron = "${tier.period-end.cron}", zone = "UTC")
    public void run() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        if (yesterday.getMonth() == Month.DECEMBER && yesterday.getDayOfMonth() == 31) {
            periodEndService.evaluate(yesterday);
        }
    }
}
