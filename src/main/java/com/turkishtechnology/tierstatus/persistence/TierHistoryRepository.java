package com.turkishtechnology.tierstatus.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TierHistoryRepository extends JpaRepository<TierHistoryEntity, String> {
    boolean existsByDecisionKey(String decisionKey);
    List<TierHistoryEntity> findByMemberIdAndEffectiveDateBetweenOrderByEffectiveDateDescIdDesc(
            String memberId, LocalDate from, LocalDate to, Pageable pageable);

    @Query("select h from TierHistoryEntity h where h.memberId = :memberId " +
            "and h.qualificationPeriod < :periodYear " +
            "and (h.validUntil is null or h.validUntil >= :today) " +
            "order by h.createdAt desc, h.id desc")
    List<TierHistoryEntity> findPriorValidChanges(
            @Param("memberId") String memberId,
            @Param("periodYear") int periodYear,
            @Param("today") LocalDate today,
            Pageable pageable);
}
