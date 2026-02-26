package com.myorg.lsf.outbox.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.outbox.OutboxWriter;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@EmbeddedKafka(partitions = 1, topics = {OutboxRetryLaterTest.TOPIC})
@SpringBootTest(
        classes = {OutboxPublisherITApp.class, OutboxRetryLaterSkipLockedTest.HooksConfig.class},
        properties = {
                "lsf.outbox.enabled=true",
                "lsf.outbox.publisher.enabled=true",
                "lsf.outbox.publisher.scheduling-enabled=false",
                "lsf.outbox.publisher.batch-size=10",
                "lsf.outbox.publisher.lease=500ms",
                "lsf.outbox.publisher.backoff-base=100ms",
                "lsf.outbox.publisher.backoff-max=500ms",
                "lsf.outbox.publisher.max-retries=5",
                "lsf.outbox.publisher.send-timeout=5s",

                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "lsf.outbox.publisher.claim-strategy=SKIP_LOCKED"
        }
)
class OutboxRetryLaterSkipLockedTest extends PostgresContainerBase {

    static final String TOPIC = "demo-topic";

    @Autowired OutboxWriter writer;
    @Autowired OutboxPublisher publisher;
    @Autowired JdbcOutboxRepository repo;

    @Autowired ConsumerFactory<String, EventEnvelope> consumerFactory;
    @Autowired ObjectMapper mapper;

    @Test
    void publishFail_thenRetryLater_shouldPublishOk() throws Exception {
        String eventId = "E_RETRY_" + java.util.UUID.randomUUID();

        EventEnvelope env = EventEnvelope.builder()
                .eventId(eventId)
                .eventType("demo.order.created.v1")
                .version(1)
                .producer("it")
                .occurredAtMs(System.currentTimeMillis())
                .payload(mapper.createObjectNode().put("y", 2))
                .build();

        writer.append(env, TOPIC, "k1");

        // 1st run: fail -> RETRY
        publisher.runOnce();
        assertEquals("RETRY", repo.statusByEventId(eventId));

        Thread.sleep(150);

        // 2nd run: publish ok -> SENT
        publisher.runOnce();
        assertEquals("SENT", repo.statusByEventId(eventId));

        Consumer<String, EventEnvelope> c = consumerFactory.createConsumer("it2", "it2");
        c.subscribe(java.util.List.of(TOPIC));
        ConsumerRecord<String, EventEnvelope> rec =
                KafkaTestUtils.getSingleRecord(c, TOPIC, Duration.ofSeconds(5));

        assertNotNull(rec.value());
        assertEquals(eventId, rec.value().getEventId());
        c.close();
    }

    @TestConfiguration
    static class HooksConfig {
        private final AtomicBoolean failOnce = new AtomicBoolean(true);

        @Bean
        OutboxPublisherHooks outboxPublisherHooks() {
            return new OutboxPublisherHooks() {
                @Override
                public void beforeSend(OutboxRow row) {
                    if (failOnce.compareAndSet(true, false)) {
                        throw new RuntimeException("Simulated publish failure");
                    }
                }
            };
        }

        @Bean
        KafkaTemplate<String, Object> kafkaTemplate(EmbeddedKafkaBroker broker) {
            Map<String, Object> props = KafkaTestUtils.producerProps(broker);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }

        @Bean
        ConsumerFactory<String, EventEnvelope> consumerFactory(EmbeddedKafkaBroker broker) {
            Map<String, Object> props = KafkaTestUtils.consumerProps("it-g2", "false", broker);
            props.put("key.deserializer", StringDeserializer.class);
            props.put("value.deserializer", JsonDeserializer.class);
            props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
            props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.myorg.lsf.contracts.core.envelope.EventEnvelope");
            props.put("auto.offset.reset", "earliest");
            return new DefaultKafkaConsumerFactory<>(new HashMap<>(props));
        }
    }
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @org.junit.jupiter.api.BeforeEach
    void clean() {
        jdbc.update("TRUNCATE TABLE lsf_outbox");
    }
}