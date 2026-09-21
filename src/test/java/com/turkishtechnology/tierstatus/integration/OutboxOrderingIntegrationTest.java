package com.turkishtechnology.tierstatus.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.turkishtechnology.tierstatus.outbox.OutboxStore;
import com.turkishtechnology.tierstatus.persistence.OutboxEventEntity;
import com.turkishtechnology.tierstatus.persistence.OutboxEventRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "tier.roles.consumer-enabled=false",
        "tier.roles.outbox-enabled=false",
        "tier.roles.period-end-enabled=false",
        "spring.kafka.admin.auto-create=false"
})
@Testcontainers(disabledWithoutDocker = true)
class OutboxOrderingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    OutboxEventRepository repository;

    @Autowired
    OutboxStore store;

    @Test
    void laterMemberEventWaitsForEarlierFailedPublication() {
        Instant now = Instant.parse("2026-09-20T12:00:00Z");
        OutboxEventEntity first = repository.saveAndFlush(new OutboxEventEntity(
                "member-events", "ORDER_TEST_MEMBER", "FLIGHT_FLOWN", "{}", now.minusSeconds(2)));
        OutboxEventEntity second = repository.saveAndFlush(new OutboxEventEntity(
                "member-events", "ORDER_TEST_MEMBER", "PARTNER_ACTIVITY", "{}", now.minusSeconds(1)));
        OutboxEventEntity anotherMember = repository.saveAndFlush(new OutboxEventEntity(
                "member-events", "ORDER_TEST_OTHER", "FLIGHT_FLOWN", "{}", now));

        assertThat(store.claim(now)).extracting(OutboxEventEntity::getId)
                .contains(first.getId(), anotherMember.getId())
                .doesNotContain(second.getId());

        store.markFailed(first.getId(), now);
        assertThat(store.claim(now.plusSeconds(1))).extracting(OutboxEventEntity::getId)
                .doesNotContain(second.getId());

        store.markPublished(first.getId(), now.plusSeconds(1));
        assertThat(store.claim(now.plusSeconds(1))).extracting(OutboxEventEntity::getId)
                .contains(second.getId());
    }
}
