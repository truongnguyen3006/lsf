package com.demo.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfPublisher;
import com.myorg.lsf.kafka.KafkaAutoConfiguration;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@Component
@RestController
@RequiredArgsConstructor
public class TestController {
    public static final String DEMO_EVENT_TYPE = "demo.hello.v1";
    private final KafkaAutoConfiguration.LsfKafkaMarker marker;
    private final LsfPublisher publisher;
    private final Environment springEnv;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Gửi message theo kiểu “framework”: publisher tự wrap EventEnvelope + headers
    @PostMapping("/send")
    public String send() {
        publisher.publish(
                "demo-topic",
                "key1",
                DEMO_EVENT_TYPE,
                "agg-1",
                HelloPayload.builder().hello("world").build()
        );
        return "sent";
    }

    @PostMapping("/send-dup")
    public String sendDup(@RequestParam(name = "eventId", defaultValue = "E1") String eventId) throws Exception {
        EventEnvelope env = EventEnvelope.builder()
                .eventId(eventId)
                .eventType(DEMO_EVENT_TYPE)
                .version(1)
                .aggregateId("agg-1")
                .correlationId("corr-1")
                .causationId(null)
                .occurredAtMs(System.currentTimeMillis())
                .producer("manual-test")
                .payload(new ObjectMapper().valueToTree(HelloPayload.builder().hello("world").build()))
                .build();

        // Gửi 2 partition khác nhau để 2 instance khác nhau xử lý (khi chạy 2 instance)
        try {
            kafkaTemplate.send(new ProducerRecord<>("demo-topic", 0, "k0", env)).get();
            Thread.sleep(200);
            kafkaTemplate.send(new ProducerRecord<>("demo-topic", 1, "k1", env)).get();
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();

            log.error("Send failed for eventId={} root={}", eventId, root.toString(), e);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Send failed: " + root.getClass().getSimpleName() + ": " + root.getMessage(),
                    e
            );
        }

        return "sent duplicate eventId=" + eventId;
    }

    /**
     * Demo retry/DLQ:
     * - Use eventId starting with FAIL_ (e.g. FAIL_E11)
     * - Handler will throw -> observe retries (per lsf.kafka.consumer.retry.*)
     * - After retries exhausted -> message is published to demo-topic.DLQ
     */
    @PostMapping("/send-fail")
    public String sendFail(
            @RequestParam(name = "eventId", defaultValue = "FAIL_E1") String eventId,
            @RequestParam(name = "partition", defaultValue = "0") int partition
    ) throws Exception {
        EventEnvelope env = EventEnvelope.builder()
                .eventId(eventId)
                .eventType(DEMO_EVENT_TYPE)
                .version(1)
                .aggregateId("agg-1")
                .correlationId("corr-1")
                .causationId(null)
                .occurredAtMs(System.currentTimeMillis())
                .producer("manual-test")
                .payload(new ObjectMapper().valueToTree(HelloPayload.builder().hello("world").build()))
                .build();

        kafkaTemplate.send(new ProducerRecord<>("demo-topic", partition, "k-fail", env)).get();
        return "sent fail eventId=" + eventId + " partition=" + partition;
    }

    @PostMapping("/send-unknown")
    public String sendUnknown(
            @RequestParam(name = "eventId", defaultValue = "E_UNKNOWN_1") String eventId,
            @RequestParam(name = "eventType", defaultValue = "demo.unknown.v1") String eventType
    ) throws Exception {

        EventEnvelope env = EventEnvelope.builder()
                .eventId(eventId)
                .eventType(eventType) // ✅ eventType không có handler
                .version(1)
                .aggregateId("agg-1")
                .correlationId("corr-1")
                .causationId(null)
                .occurredAtMs(System.currentTimeMillis())
                .producer("manual-test")
                .payload(new ObjectMapper().valueToTree(HelloPayload.builder().hello("world").build()))
                .build();

        kafkaTemplate.send(new ProducerRecord<>("demo-topic", 0, "k0", env)).get();
        return "sent unknown eventType=" + eventType + ", eventId=" + eventId;
    }

    @PostMapping("/send-one")
    public String sendOne(
            @RequestParam(name="eventId") String eventId,
            @RequestParam(name="eventType", defaultValue = DEMO_EVENT_TYPE) String eventType,
            @RequestParam(name="partition", defaultValue="0") int partition
    ) throws Exception {
        EventEnvelope env = EventEnvelope.builder()
                .eventId(eventId)
                .eventType(eventType)
                .version(1)
                .aggregateId("agg-1")
                .correlationId("corr-1")
                .causationId(null)
                .occurredAtMs(System.currentTimeMillis())
                .producer("manual-test")
                .payload(new ObjectMapper().valueToTree(HelloPayload.builder().hello("world").build()))
                .build();

        kafkaTemplate.send(new ProducerRecord<>("demo-topic", partition, "k", env)).get();
        return "sent one eventId=" + eventId + " partition=" + partition;
    }

    @PostConstruct
    void logKafkaBootstrap() {
        Object bs = kafkaTemplate.getProducerFactory()
                .getConfigurationProperties()
                .get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);
        log.info("IT KafkaTemplate bootstrap.servers={}", bs);
    }

    @PostConstruct
    void logKafkaProducerBootstrap() {
        Object bs = kafkaTemplate.getProducerFactory()
                .getConfigurationProperties()
                .get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);
        log.info("KafkaTemplate bootstrap.servers={}", bs);
    }



}
