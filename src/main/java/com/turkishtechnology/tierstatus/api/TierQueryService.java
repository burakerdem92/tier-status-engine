package com.turkishtechnology.tierstatus.api;

import com.turkishtechnology.tierstatus.domain.Tier;
import com.turkishtechnology.tierstatus.persistence.MemberPeriodRepository;
import com.turkishtechnology.tierstatus.persistence.MemberStatusRepository;
import com.turkishtechnology.tierstatus.persistence.MileageLedgerRepository;
import com.turkishtechnology.tierstatus.persistence.TierHistoryRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TierQueryService {

    private final MemberStatusRepository memberStatuses;
    private final MemberPeriodRepository memberPeriods;
    private final TierHistoryRepository tierHistory;
    private final MileageLedgerRepository ledger;
    private final Clock clock;

    public TierQueryService(MemberStatusRepository memberStatuses,
                            MemberPeriodRepository memberPeriods,
                            TierHistoryRepository tierHistory,
                            MileageLedgerRepository ledger,
                            Clock clock) {
        this.memberStatuses = memberStatuses;
        this.memberPeriods = memberPeriods;
        this.tierHistory = tierHistory;
        this.ledger = ledger;
        this.clock = clock;
    }

    public CurrentTierResponse currentTier(String memberId) {
        var status = memberStatuses.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
        int year = LocalDate.now(clock).getYear();
        int miles = memberPeriods.findByMemberIdAndPeriodYear(memberId, year)
                .map(period -> period.getTotalStatusMiles())
                .orElse(0);
        Tier next = status.getTier().next();
        Integer milesToNext = next == null ? null
                : Math.max(0, next.qualificationThreshold() - miles);
        Integer renewalThreshold = status.getTier() == Tier.CLASSIC ? null
                : status.getTier().renewalThreshold();
        Integer milesToRenew = renewalThreshold == null ? null
                : Math.max(0, renewalThreshold - miles);

        return new CurrentTierResponse(
                memberId, status.getTier(), status.getValidUntil(), year, miles,
                next, milesToNext, renewalThreshold, milesToRenew, clock.instant());
    }

    public List<TierHistoryResponse> history(String memberId, LocalDate from,
                                             LocalDate to, int limit) {
        requireMember(memberId);
        return tierHistory.findByMemberIdAndEffectiveDateBetweenOrderByEffectiveDateDescIdDesc(
                        memberId, from, to, PageRequest.of(0, boundedLimit(limit)))
                .stream()
                .map(item -> new TierHistoryResponse(
                        item.getEffectiveDate(), item.getPreviousTier(), item.getNewTier(),
                        item.getChangeType(), item.getQualificationPeriod(),
                        item.getStatusMilesInPeriod(), item.getValidUntil(),
                        item.getTriggeredByEventId()))
                .toList();
    }

    public List<LedgerMovementResponse> movements(String memberId, LocalDate from,
                                                  LocalDate to, int limit) {
        requireMember(memberId);
        return ledger.findByMemberIdAndActivityDateBetweenOrderByActivityDateDescIdDesc(
                        memberId, from, to, PageRequest.of(0, boundedLimit(limit)))
                .stream()
                .map(item -> new LedgerMovementResponse(
                        item.getSourceEventId(), item.getEventType(), item.getSource(),
                        item.getActivityDate(), item.getStatusMiles(), item.getPeriodYear(),
                        item.getRateVersion()))
                .toList();
    }

    private void requireMember(String memberId) {
        if (!memberStatuses.existsById(memberId)) {
            throw new MemberNotFoundException(memberId);
        }
    }

    private static int boundedLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return Math.min(limit, 200);
    }
}
