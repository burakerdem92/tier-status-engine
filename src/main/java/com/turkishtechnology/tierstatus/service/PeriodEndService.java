package com.turkishtechnology.tierstatus.service;

import com.turkishtechnology.tierstatus.persistence.MemberPeriodRepository;
import com.turkishtechnology.tierstatus.persistence.MemberStatusRepository;
import java.time.LocalDate;
import java.time.Month;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeriodEndService {

    private final MemberStatusRepository memberStatuses;
    private final MemberPeriodRepository memberPeriods;
    private final TierDecisionService tierDecisionService;

    public PeriodEndService(MemberStatusRepository memberStatuses,
                            MemberPeriodRepository memberPeriods,
                            TierDecisionService tierDecisionService) {
        this.memberStatuses = memberStatuses;
        this.memberPeriods = memberPeriods;
        this.tierDecisionService = tierDecisionService;
    }

    @Transactional
    public int evaluate(LocalDate periodEnd) {
        if (periodEnd.getMonth() != Month.DECEMBER || periodEnd.getDayOfMonth() != 31) {
            throw new IllegalArgumentException("periodEnd must be 31 December");
        }
        int evaluated = 0;
        // Select ids so this transaction does not cache an unlocked, stale status entity.
        for (String memberId : memberStatuses.findMemberIdsByValidUntil(periodEnd)) {
            // The event processor also locks the period before changing member status.
            // Read under that same lock so a concurrent movement cannot yield a stale decision.
            int total = memberPeriods.findForUpdate(
                            memberId, periodEnd.getYear())
                    .map(period -> period.getTotalStatusMiles())
                    .orElse(0);
            tierDecisionService.evaluatePeriodEnd(memberId, periodEnd, total);
            evaluated++;
        }
        return evaluated;
    }
}
