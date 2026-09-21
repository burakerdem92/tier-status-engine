package com.turkishtechnology.tierstatus.config;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                          @Value("${tier.kafka.dlt-topic}") String dltTopic) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(dltTopic, record.partition()));
        ExponentialBackOff backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(10_000);
        backOff.setMaxElapsedTime(31_000);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<?, ?> ingestionKafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);
        // A failed raw_event transaction must not acknowledge the source message.
        // Validation failures are stored as INVALID by IngestionService instead.
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(5_000L, FixedBackOff.UNLIMITED_ATTEMPTS)));
        return factory;
    }

    @Bean
    NewTopic inputTopic(@Value("${tier.kafka.input-topic}") String name,
                        @Value("${tier.kafka.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic memberTopic(@Value("${tier.kafka.member-topic}") String name,
                         @Value("${tier.kafka.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic changedTopic(@Value("${tier.kafka.changed-topic}") String name,
                          @Value("${tier.kafka.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic dltTopic(@Value("${tier.kafka.dlt-topic}") String name,
                      @Value("${tier.kafka.partitions}") int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
