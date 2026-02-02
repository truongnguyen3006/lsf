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

            RestTemplate rt = new RestTemplate();

            String eventId = "E_MEM_2";
            rt.postForObject("http://localhost:" + a.port() + "/send-dup?eventId=" + eventId, null, String.class);

            TestHandlerProbe pa = a.getBean(TestHandlerProbe.class);
            TestHandlerProbe pb = b.getBean(TestHandlerProbe.class);

            Awaitility.await().atMost(Duration.ofSeconds(15))
                    .until(() -> (pa.handled() + pb.handled()) == 2);

            assertEquals(2, pa.handled() + pb.handled());
        }
    }
}
