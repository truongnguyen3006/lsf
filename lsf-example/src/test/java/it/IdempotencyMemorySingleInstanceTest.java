package it;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IdempotencyMemorySingleInstanceTest extends AbstractIntegrationTest {

    @Test
    void memory_singleInstance_shouldHandleOnce_andSkipDuplicate() {
        // TODO: thay bằng main app class của bạn
        Class<?> mainApp = com.demo.app.DemoAppApplication.class;

        try (AppInstance app = new AppInstance(mainApp, "it-app-1", "memory")) {
            RestTemplate rt = new RestTemplate();

            String eventId = "E_MEM_1";
            rt.postForObject("http://localhost:" + app.port() + "/send-dup?eventId=" + eventId, null, String.class);

            TestHandlerProbe probe = app.getBean(TestHandlerProbe.class);

            // chờ handler được gọi đúng 1 lần
            Awaitility.await().atMost(Duration.ofSeconds(10))
                    .until(() -> probe.handled() == 1);

            // đợi thêm chút để chắc record thứ 2 đã tới (tránh race)
            Awaitility.await().pollDelay(Duration.ofMillis(500)).until(() -> probe.handled() == 1);

            assertEquals(1, probe.handled());
        }
    }
}