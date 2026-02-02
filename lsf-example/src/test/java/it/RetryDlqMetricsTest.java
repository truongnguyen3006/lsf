package it;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RetryDlqMetricsTest extends AbstractIntegrationTest {

    @Test
    void fail_shouldRetry_andGoToDlq_metric() {
        Class<?> mainApp = com.demo.app.DemoAppApplication.class;

        try (AppInstance app = new AppInstance(mainApp, "it-fail", "redis")) {
            RestTemplate rt = new RestTemplate();

            // gửi 1 record FAIL_ để khỏi nhiễu (endpoint send-one)
            String eventId = "FAIL_IT_1";
            rt.postForObject("http://localhost:" + app.port()
                    + "/send-one?eventId=" + eventId + "&partition=0", null, String.class);

            // chờ dlq metric tăng lên >= 1
            Awaitility.await().atMost(Duration.ofSeconds(20))
                    .until(() -> metricCount(rt, app.port(), "lsf.kafka.dlq") >= 1);

            long retry = metricCount(rt, app.port(), "lsf.kafka.retry");
            long dlq = metricCount(rt, app.port(), "lsf.kafka.dlq");

            assertTrue(retry >= 1, "expect retry >= 1 but was " + retry);
            assertTrue(dlq >= 1, "expect dlq >= 1 but was " + dlq);
        }
    }

    @SuppressWarnings("unchecked")
    private static long metricCount(RestTemplate rt, int port, String name) {
        Map<String, Object> m = rt.getForObject(
                "http://localhost:" + port + "/actuator/metrics/" + name,
                Map.class
        );
        List<Map<String, Object>> measurements = (List<Map<String, Object>>) m.get("measurements");
        Map<String, Object> first = measurements.get(0);
        Number value = (Number) first.get("value");
        return value.longValue();
    }
}