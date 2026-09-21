package com.turkishtechnology.tierstatus.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingReversalRepository extends JpaRepository<PendingReversalEntity, String> {
}
