package it;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IdempotencyMemorySingleInstanceTest extends AbstractIntegrationTest {

    @Test
    void memory_singleInstance_shouldHandleOnce_andSkipDuplicate() {
        Class<?> mainApp = com.demo.app.DemoAppApplication.class;

        try (AppInstance app = new AppInstance(mainApp, "it-app-1", "memory")) {
            app.waitForTopicAssignment("demo-topic", 2);
            awaitGroupReady("it-group", "demo-topic", 1, 2, Duration.ofSeconds(20));

            RestTemplate rt = new RestTemplate();

            String eventId = "E_MEM_1";
            rt.postForObject("http://localhost:" + app.port() + "/send-dup?eventId=" + eventId, null, String.class);

            // chờ handler được gọi đúng 1 lần
            Awaitility.await().atMost(Duration.ofSeconds(10))
                    .until(() -> Metrics.count(rt, app.port(), "lsf.it.handled") == 1);

            // đợi thêm chút để chắc record thứ 2 đã tới (tránh race)
            Awaitility.await().pollDelay(Duration.ofMillis(500))
                    .until(() -> Metrics.count(rt, app.port(), "lsf.it.handled") == 1);

            assertEquals(1, Metrics.count(rt, app.port(), "lsf.it.handled"));

        }
    }
}