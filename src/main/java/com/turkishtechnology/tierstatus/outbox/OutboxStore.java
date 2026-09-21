package com.turkishtechnology.tierstatus.outbox;

import com.turkishtechnology.tierstatus.domain.OutboxStatus;
import com.turkishtechnology.tierstatus.persistence.OutboxEventEntity;
import com.turkishtechnology.tierstatus.persistence.OutboxEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxStore {

    private final OutboxEventRepository repository;
    private final int batchSize;
    private final long staleClaimSeconds;

    public OutboxStore(OutboxEventRepository repository,
                       @Value("${tier.outbox.batch-size}") int batchSize,
                       @Value("${tier.outbox.stale-claim-seconds}") long staleClaimSeconds) {
        this.repository = repository;
        this.batchSize = batchSize;
        this.staleClaimSeconds = staleClaimSeconds;
    }

    @Transactional
    public List<OutboxEventEntity> claim(Instant now) {
        List<OutboxEventEntity> events = repository.findClaimable(
                OutboxStatus.PENDING,
                OutboxStatus.PROCESSING,
                OutboxStatus.PUBLISHED,
                now,
                now.minusSeconds(staleClaimSeconds),
                PageRequest.of(0, batchSize));
        events.forEach(event -> event.claim(now));
        repository.flush();
        return List.copyOf(events);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(String id, Instant now) {
        repository.findById(id).ifPresent(event -> event.markPublished(now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String id, Instant now) {
        repository.findById(id).ifPresent(event -> {
            long delaySeconds = Math.min(300, 1L << Math.min(event.getAttempts(), 8));
            event.markFailed(now.plus(Duration.ofSeconds(delaySeconds)));
        });
    }
}
