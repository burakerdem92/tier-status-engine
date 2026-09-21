package com.turkishtechnology.tierstatus.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawEventRepository extends JpaRepository<RawEventEntity, String> {
    boolean existsByIngestionKey(String ingestionKey);
    Optional<RawEventEntity> findByIngestionKey(String ingestionKey);
    Optional<RawEventEntity> findByEventId(String eventId);
}
