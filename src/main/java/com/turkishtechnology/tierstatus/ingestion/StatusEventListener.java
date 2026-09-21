package com.turkishtechnology.tierstatus.ingestion;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tier.roles.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class StatusEventListener {

    private final IngestionService ingestionService;

    public StatusEventListener(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @KafkaListener(topics = "${tier.kafka.input-topic}",
            groupId = "${spring.kafka.consumer.group-id}-ingestion",
            containerFactory = "ingestionKafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, String> record) {
        ingestionService.ingest(
                record.topic(), record.partition(), record.offset(), record.value());
    }
}
