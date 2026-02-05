package it;

import com.myorg.lsf.kafka.LsfDlqHeaders;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class DlqReasonHeaderTest extends AbstractIntegrationTest {

    @Test
    void unknownEventType_shouldGoDlq_withReasonHeader() {
        Class<?> mainApp = com.demo.app.DemoAppApplication.class;

        try (AppInstance app = new AppInstance(mainApp, "it-dlq-reason", "redis")) {
            RestTemplate rt = new RestTemplate();

            try (KafkaConsumer<byte[], byte[]> c = newRawConsumer("it-dlq-check-" + System.nanoTime())) {
                c.subscribe(java.util.List.of("demo-topic.DLQ"));

                // trigger assignment then skip old records
                c.poll(Duration.ofMillis(300));
                if (!c.assignment().isEmpty()) {
                    c.seekToEnd(c.assignment());
                }

                // send unknown eventType -> should be NON_RETRYABLE with reason UNKNOWN_EVENT_TYPE
                String eventId = "UNK_IT_1";
                rt.postForObject("http://localhost:" + app.port()
                        + "/send-unknown?eventId=" + eventId + "&eventType=demo.unknown.v1", null, String.class);

                AtomicReference<ConsumerRecord<byte[], byte[]>> ref = new AtomicReference<>();

                Awaitility.await().atMost(Duration.ofSeconds(20))
                        .until(() -> {
                            ConsumerRecords<byte[], byte[]> recs = c.poll(Duration.ofMillis(500));
                            for (ConsumerRecord<byte[], byte[]> r : recs) {
                                ref.set(r);
                                return true;
                            }
                            return false;
                        });

                ConsumerRecord<byte[], byte[]> r = ref.get();
                assertNotNull(r);

                assertHeaderEquals(r, LsfDlqHeaders.REASON, "UNKNOWN_EVENT_TYPE");
                assertHeaderEquals(r, LsfDlqHeaders.NON_RETRYABLE, "true");
                assertHeaderPresent(r, LsfDlqHeaders.EXCEPTION_CLASS);
                assertHeaderPresent(r, LsfDlqHeaders.SERVICE);
                assertHeaderPresent(r, LsfDlqHeaders.TS_MS);
            }
        }
    }

    private static KafkaConsumer<byte[], byte[]> newRawConsumer(String groupId) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        return new KafkaConsumer<>(p);
    }

    private static void assertHeaderEquals(ConsumerRecord<?, ?> r, String key, String expected) {
        Header h = r.headers().lastHeader(key);
        assertNotNull(h, "Missing header: " + key);
        String actual = new String(h.value(), StandardCharsets.UTF_8);
        assertEquals(expected, actual, "Header mismatch for " + key);
    }

    private static void assertHeaderPresent(ConsumerRecord<?, ?> r, String key) {
        Header h = r.headers().lastHeader(key);
        assertNotNull(h, "Missing header: " + key);
    }
}
