package it;

import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

public final class Metrics {
    private Metrics() {}

    @SuppressWarnings("unchecked")
    public static long count(RestTemplate rt, int port, String name) {
        Map<String, Object> m = rt.getForObject(
                "http://localhost:" + port + "/actuator/metrics/" + name,
                Map.class
        );
        List<Map<String, Object>> measurements = (List<Map<String, Object>>) m.get("measurements");
        Number value = (Number) measurements.get(0).get("value");
        return value.longValue();
    }
}
