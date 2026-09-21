package com.turkishtechnology.tierstatus.outbox;

import com.turkishtechnology.tierstatus.persistence.OutboxEventEntity;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tier.roles.outbox-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxStore store;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    public OutboxPublisher(OutboxStore store,
                           KafkaTemplate<String, String> kafkaTemplate,
                           Clock clock) {
        this.store = store;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tier.outbox.fixed-delay-ms}")
    public void publishBatch() {
        List<OutboxEventEntity> batch = store.claim(clock.instant());
        for (OutboxEventEntity event : batch) {
            publish(event);
        }
    }

    private void publish(OutboxEventEntity event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getTopic(), event.getMessageKey(), event.getPayload());
        record.headers().add("outboxId", event.getId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
        try {
            kafkaTemplate.send(record).get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
            store.markPublished(event.getId(), clock.instant());
        } catch (Exception exception) {
            log.warn("Outbox publish failed for {}", event.getId(), exception);
            store.markFailed(event.getId(), clock.instant());
        }
    }
}
