package it;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IdempotencyMemoryTwoInstancesTest extends AbstractIntegrationTest {

    @Test
    void memory_twoInstances_shouldHandleTwice_total() {
        Class<?> mainApp = com.demo.app.DemoAppApplication.class;

        try (AppInstance a = new AppInstance(mainApp, "it-app-a", "memory");
             AppInstance b = new AppInstance(mainApp, "it-app-b", "memory")) {
             a.waitForTopicAssignment("demo-topic", 1);
             b.waitForTopicAssignment("demo-topic", 1);
            awaitGroupReady("it-group", "demo-topic", 2, 2, Duration.ofSeconds(20));


            RestTemplate rt = new RestTemplate();

            String eventId = "E_MEM_2";
            rt.postForObject("http://localhost:" + a.port() + "/send-dup?eventId=" + eventId, null, String.class);

            Awaitility.await().atMost(Duration.ofSeconds(15))
                    .until(() -> (Metrics.count(rt, a.port(), "lsf.it.handled")
                            + Metrics.count(rt, b.port(), "lsf.it.handled")) == 2);

            assertEquals(2, Metrics.count(rt, a.port(), "lsf.it.handled")
                    + Metrics.count(rt, b.port(), "lsf.it.handled"));
        }
    }
}
