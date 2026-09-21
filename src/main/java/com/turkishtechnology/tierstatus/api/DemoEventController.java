package com.turkishtechnology.tierstatus.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.turkishtechnology.tierstatus.domain.EventEnvelope;
import com.turkishtechnology.tierstatus.domain.EventValidationException;
import com.turkishtechnology.tierstatus.ingestion.EventParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A local-only bridge from Swagger UI to the regular Kafka ingestion path. */
@RestController
@Profile("!prod")
@ConditionalOnProperty(name = "tier.demo.enabled", havingValue = "true")
@RequestMapping("/api/v1/demo")
public class DemoEventController {

    private final EventParser parser;
    private final KafkaTemplate<String, String> producer;
    private final String inputTopic;

    public DemoEventController(EventParser parser,
                               KafkaTemplate<String, String> producer,
                               @Value("${tier.kafka.input-topic}") String inputTopic) {
        this.parser = parser;
        this.producer = producer;
        this.inputTopic = inputTopic;
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> publish(@RequestBody JsonNode body) throws Exception {
        if (body == null || !body.isObject()) {
            throw new EventValidationException("Event must be a JSON object");
        }

        String json = body.toString();
        EventEnvelope event = parser.parse(json);
        producer.send(inputTopic, event.memberId(), json).get(10, TimeUnit.SECONDS);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("eventId", event.eventId());
        result.put("memberId", event.memberId());
        result.put("status", "ACCEPTED_BY_KAFKA");
        result.put("note", "Processing continues asynchronously. Query the member tier after a few seconds.");
        return ResponseEntity.accepted().body(result);
    }
}
