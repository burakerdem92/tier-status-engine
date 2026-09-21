package com.turkishtechnology.tierstatus.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberPeriodRepository extends JpaRepository<MemberPeriodEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select period from MemberPeriodEntity period " +
            "where period.memberId = :memberId and period.periodYear = :periodYear")
    Optional<MemberPeriodEntity> findForUpdate(@Param("memberId") String memberId,
                                                @Param("periodYear") int periodYear);

    Optional<MemberPeriodEntity> findByMemberIdAndPeriodYear(String memberId, int periodYear);
}
