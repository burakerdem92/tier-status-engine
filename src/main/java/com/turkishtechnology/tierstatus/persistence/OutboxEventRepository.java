package com.turkishtechnology.tierstatus.persistence;

import com.turkishtechnology.tierstatus.domain.OutboxStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from OutboxEventEntity event where " +
            "((event.status = :pending and (event.nextAttemptAt is null or event.nextAttemptAt <= :now)) " +
            "or (event.status = :processing and event.claimedAt < :staleBefore)) " +
            "and not exists (select earlier.id from OutboxEventEntity earlier where " +
            "earlier.topic = event.topic and earlier.messageKey = event.messageKey " +
            "and earlier.status <> :published " +
            "and (earlier.createdAt < event.createdAt " +
            "or (earlier.createdAt = event.createdAt and earlier.id < event.id))) " +
            "order by event.createdAt, event.id")
    List<OutboxEventEntity> findClaimable(@Param("pending") OutboxStatus pending,
                                          @Param("processing") OutboxStatus processing,
                                          @Param("published") OutboxStatus published,
                                          @Param("now") Instant now,
                                          @Param("staleBefore") Instant staleBefore,
                                          Pageable pageable);
}
