package com.turkishtechnology.tierstatus.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.turkishtechnology.tierstatus.persistence.MemberPeriodRepository;
import com.turkishtechnology.tierstatus.persistence.MemberStatusRepository;
import com.turkishtechnology.tierstatus.persistence.MileageLedgerRepository;
import com.turkishtechnology.tierstatus.persistence.RawEventRepository;
import com.turkishtechnology.tierstatus.persistence.TierHistoryRepository;
import com.turkishtechnology.tierstatus.domain.ProcessingStatus;
import com.turkishtechnology.tierstatus.domain.Tier;
import com.turkishtechnology.tierstatus.domain.TierChangeType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DuplicateDeliveryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("tier.outbox.fixed-delay-ms", () -> "100");
    }

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    MileageLedgerRepository ledger;
    @Autowired
    MemberPeriodRepository periods;
    @Autowired
    MemberStatusRepository statuses;
    @Autowired
    TierHistoryRepository history;
    @Autowired
    RawEventRepository rawEvents;

    @Test
    void duplicateEventHasExactlyOneBusinessEffect() throws Exception {
        String event = """
                {
                  "eventId":"e7c1b6d2-3f4a-4c8e-9a51-2f0d8b7c1a90",
                  "eventType":"FLIGHT_FLOWN",
                  "eventTime":"2026-09-05T14:32:10Z",
                  "source":"DCS",
                  "memberId":"TK123456789",
                  "payload":{
                    "operatingCarrier":"TK","bookingClass":"J",
                    "flightDate":"2026-09-05","distanceMiles":1552
                  }
                }
                """;

        kafkaTemplate.send("status-events", "source-a", event).get();
        kafkaTemplate.send("status-events", "source-b", event).get();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(ledger.count()).isEqualTo(1);
            assertThat(periods.findByMemberIdAndPeriodYear("TK123456789", 2026))
                    .get()
                    .extracting(period -> period.getTotalStatusMiles())
                    .isEqualTo(2328);
        });
    }

    @Test
    void reversalBeforeOriginalDoesNotGrantTierOnZeroNetMiles() throws Exception {
        String memberId = "CANCEL_BEFORE_ORIGINAL_TEST";
        String originalId = "ff111111-1111-4111-8111-111111111111";
        String reversalId = "ff222222-2222-4222-8222-222222222222";
        String reversal = """
                {"eventId":"%s","eventType":"ACCRUAL_REVERSAL",
                 "eventTime":"2026-09-20T12:00:00Z","source":"REVENUE_ACCOUNTING",
                 "memberId":"%s","payload":{"originalEventId":"%s","reason":"REFUND"}}
                """.formatted(reversalId, memberId, originalId);
        String original = """
                {"eventId":"%s","eventType":"FLIGHT_FLOWN",
                 "eventTime":"2026-09-20T12:01:00Z","source":"DCS",
                 "memberId":"%s","payload":{"operatingCarrier":"TK","bookingClass":"Y",
                 "flightDate":"2026-09-20","distanceMiles":40000}}
                """.formatted(originalId, memberId);

        kafkaTemplate.send("status-events", "different-source", reversal).get();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(rawEvents.findByEventId(reversalId))
                        .get().extracting(raw -> raw.getProcessingStatus())
                        .isEqualTo(ProcessingStatus.WAITING_FOR_ORIGINAL));

        kafkaTemplate.send("status-events", "another-source", original).get();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(periods.findByMemberIdAndPeriodYear(memberId, 2026))
                    .get().extracting(period -> period.getTotalStatusMiles())
                    .isEqualTo(0);
            assertThat(rawEvents.findByEventId(reversalId))
                    .get().extracting(raw -> raw.getProcessingStatus())
                    .isEqualTo(ProcessingStatus.PROCESSED);
            assertThat(statuses.findById(memberId)).get()
                    .extracting(status -> status.getTier()).isEqualTo(Tier.CLASSIC);
            assertThat(history.findByMemberIdAndEffectiveDateBetweenOrderByEffectiveDateDescIdDesc(
                    memberId, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 12, 31),
                    PageRequest.of(0, 10))).isEmpty();
        });
    }

    @Test
    void malformedEventsAreStillStoredAsRawInvalidRows() throws Exception {
        String invalidId = "x".repeat(101);
        String tooLong = """
                {"eventId":"%s","eventType":"FLIGHT_FLOWN",
                 "eventTime":"2026-09-20T12:00:00Z","source":"DCS",
                 "memberId":"MALFORMED_TEST","payload":{}}
                """.formatted(invalidId);
        var blankMetadata = kafkaTemplate.send("status-events", "source", "  ").get();
        var longMetadata = kafkaTemplate.send("status-events", "source", tooLong).get();

        String blankKey = "status-events:%d:%d".formatted(
                blankMetadata.getRecordMetadata().partition(), blankMetadata.getRecordMetadata().offset());
        String longKey = "status-events:%d:%d".formatted(
                longMetadata.getRecordMetadata().partition(), longMetadata.getRecordMetadata().offset());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(rawEvents.findByIngestionKey(blankKey))
                    .get().extracting(raw -> raw.getProcessingStatus())
                    .isEqualTo(ProcessingStatus.INVALID);
            assertThat(rawEvents.findByIngestionKey(longKey))
                    .get().extracting(raw -> raw.getProcessingStatus())
                    .isEqualTo(ProcessingStatus.INVALID);
        });
    }

    @Test
    void conflictingEventIdPreservesSecondRawMessageWithoutChangingLedger() throws Exception {
        String id = "cc333333-3333-4333-8333-333333333333";
        String original = """
                {"eventId":"%s","eventType":"FLIGHT_FLOWN",
                 "eventTime":"2026-09-20T12:00:00Z","source":"DCS",
                 "memberId":"CONFLICT_TEST","payload":{"operatingCarrier":"TK",
                 "bookingClass":"Y","flightDate":"2026-09-20","distanceMiles":500}}
                """.formatted(id);
        String conflicting = original.replace("\"distanceMiles\":500", "\"distanceMiles\":1000");
        kafkaTemplate.send("status-events", "source", original).get();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(rawEvents.findByEventId(id)).isPresent());

        var result = kafkaTemplate.send("status-events", "source", conflicting).get();
        String key = "status-events:%d:%d".formatted(
                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(rawEvents.findByIngestionKey(key)).get()
                    .satisfies(raw -> {
                        assertThat(raw.getProcessingStatus()).isEqualTo(ProcessingStatus.INVALID);
                        assertThat(raw.getRawPayload()).isEqualTo(conflicting);
                        assertThat(raw.getEventId()).isNull();
                    });
            assertThat(ledger.findBySourceEventId(id)).isPresent();
        });
    }

    @Test
    void currentPeriodFlightCancellationRevokesTheUpgradeItCreated() throws Exception {
        String memberId = "CURRENT_PERIOD_CANCEL_UPGRADE";
        LocalDate activityDate = LocalDate.now(Clock.systemUTC());
        String eventTime = Instant.now().toString();
        String first = """
                {"eventId":"a1111111-1111-4111-8111-111111111111",
                 "eventType":"MANUAL_ADJUSTMENT","eventTime":"%s","source":"CALL_CENTER",
                 "memberId":"%s","payload":{"statusMiles":23000,
                 "effectiveDate":"%s","reason":"INITIAL_BALANCE"}}
                """.formatted(eventTime, memberId, activityDate);
        String flight = """
                {"eventId":"a2222222-2222-4222-8222-222222222222",
                 "eventType":"FLIGHT_FLOWN","eventTime":"%s","source":"DCS",
                 "memberId":"%s","payload":{"operatingCarrier":"TK","bookingClass":"Y",
                 "flightDate":"%s","distanceMiles":5000}}
                """.formatted(eventTime, memberId, activityDate);
        String reversal = """
                {"eventId":"a3333333-3333-4333-8333-333333333333",
                 "eventType":"ACCRUAL_REVERSAL","eventTime":"%s","source":"REVENUE_ACCOUNTING",
                 "memberId":"%s","payload":{"originalEventId":
                 "a2222222-2222-4222-8222-222222222222","reason":"TICKET_REFUNDED"}}
                """.formatted(eventTime, memberId);

        kafkaTemplate.send("status-events", memberId, first).get();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(periods.findByMemberIdAndPeriodYear(memberId, activityDate.getYear()))
                        .get().extracting(period -> period.getTotalStatusMiles()).isEqualTo(23_000));
        kafkaTemplate.send("status-events", memberId, flight).get();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statuses.findById(memberId)).get()
                        .extracting(status -> status.getTier()).isEqualTo(Tier.CLASSIC_PLUS));

        kafkaTemplate.send("status-events", memberId, reversal).get();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(periods.findByMemberIdAndPeriodYear(memberId, activityDate.getYear()))
                    .get().extracting(period -> period.getTotalStatusMiles()).isEqualTo(23_000);
            assertThat(statuses.findById(memberId)).get().satisfies(status -> {
                assertThat(status.getTier()).isEqualTo(Tier.CLASSIC);
                assertThat(status.getValidUntil()).isNull();
            });
            assertThat(history.findByMemberIdAndEffectiveDateBetweenOrderByEffectiveDateDescIdDesc(
                    memberId, activityDate, activityDate, PageRequest.of(0, 10)))
                    .extracting(item -> item.getChangeType())
                    .containsExactlyInAnyOrder(TierChangeType.UPGRADE, TierChangeType.DOWNGRADE);
            assertThat(ledger.findBySourceEventId("a3333333-3333-4333-8333-333333333333"))
                    .get().extracting(item -> item.getStatusMiles()).isEqualTo(-5_000);
        });
    }
}
