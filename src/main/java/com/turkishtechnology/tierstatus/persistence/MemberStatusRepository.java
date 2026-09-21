package com.turkishtechnology.tierstatus.persistence;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberStatusRepository extends JpaRepository<MemberStatusEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select status from MemberStatusEntity status where status.memberId = :memberId")
    Optional<MemberStatusEntity> findForUpdate(@Param("memberId") String memberId);

    @Query("select status.memberId from MemberStatusEntity status " +
            "where status.validUntil = :validUntil")
    List<String> findMemberIdsByValidUntil(@Param("validUntil") LocalDate validUntil);
}
