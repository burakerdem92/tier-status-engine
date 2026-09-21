package com.turkishtechnology.tierstatus.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "tier.roles.consumer-enabled", havingValue = "true", matchIfMissing = true)
public class MemberEventListener {

    private final TierProcessingService processingService;

    public MemberEventListener(TierProcessingService processingService) {
        this.processingService = processingService;
    }

    @KafkaListener(topics = "${tier.kafka.member-topic}", groupId = "${spring.kafka.consumer.group-id}-processor")
    public void onMessage(ConsumerRecord<String, String> record) {
        processingService.process(record.value());
    }
}
