package it;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IdempotencyRedisTwoInstancesTest extends AbstractIntegrationTest {

    @Test
    void redis_twoInstances_shouldHandleOnce_total() {
        Class<?> mainApp = com.demo.app.DemoAppApplication.class;

        try (AppInstance a = new AppInstance(mainApp, "it-redis-a", "redis");
             AppInstance b = new AppInstance(mainApp, "it-redis-b", "redis")) {
             a.waitForTopicAssignment("demo-topic", 1);
             b.waitForTopicAssignment("demo-topic", 1);
            awaitGroupReady("it-group", "demo-topic", 2, 2, Duration.ofSeconds(20));


            RestTemplate rt = new RestTemplate();

            String eventId = "E_REDIS_1";
            rt.postForObject("http://localhost:" + a.port() + "/send-dup?eventId=" + eventId, null, String.class);

            Awaitility.await().atMost(Duration.ofSeconds(15))
                    .until(() -> (Metrics.count(rt, a.port(), "lsf.it.handled")
                            + Metrics.count(rt, b.port(), "lsf.it.handled")) >= 1);

            Awaitility.await().pollDelay(Duration.ofSeconds(2))
                    .until(() -> (Metrics.count(rt, a.port(), "lsf.it.handled")
                            + Metrics.count(rt, b.port(), "lsf.it.handled")) == 1);

            assertEquals(1, Metrics.count(rt, a.port(), "lsf.it.handled")
                    + Metrics.count(rt, b.port(), "lsf.it.handled"));

        }
    }
}
