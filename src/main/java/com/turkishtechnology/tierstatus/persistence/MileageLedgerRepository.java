package com.turkishtechnology.tierstatus.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MileageLedgerRepository extends JpaRepository<MileageLedgerEntity, String> {
    Optional<MileageLedgerEntity> findBySourceEventId(String sourceEventId);
    boolean existsBySourceEventId(String sourceEventId);
    boolean existsByOriginalMovementId(String originalMovementId);
    List<MileageLedgerEntity> findByMemberIdAndActivityDateBetweenOrderByActivityDateDescIdDesc(
            String memberId, LocalDate from, LocalDate to, Pageable pageable);
}
