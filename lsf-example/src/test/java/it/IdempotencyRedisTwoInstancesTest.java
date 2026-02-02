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

            RestTemplate rt = new RestTemplate();

            String eventId = "E_REDIS_1";
            rt.postForObject("http://localhost:" + a.port() + "/send-dup?eventId=" + eventId, null, String.class);

            TestHandlerProbe pa = a.getBean(TestHandlerProbe.class);
            TestHandlerProbe pb = b.getBean(TestHandlerProbe.class);

            // chờ có ít nhất 1 handled
            Awaitility.await().atMost(Duration.ofSeconds(15))
                    .until(() -> (pa.handled() + pb.handled()) >= 1);

            // đợi thêm để chắc không bị handle lần 2
            Awaitility.await().pollDelay(Duration.ofSeconds(2))
                    .until(() -> (pa.handled() + pb.handled()) == 1);

            assertEquals(1, pa.handled() + pb.handled());
        }
    }
}
