package com.myorg.lsf.eventing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "lsf.eventing")
public class LsfEventingProperties {
    //nếu rỗng -> lấy spring.application.name nếu có
    private String producerName;
    // bật listener auto (không cần tự viết @KafkaListener
    private Listener listener = new Listener();
    //danh sách topics listener sẽ subscribe
    private List<String> consumeTopics = new ArrayList<>();
    //nếu gặp eventType không có handler: true=log+skip, false=throw
    private boolean ignoreUnknownEventType = true;

    private Idempotency idempotency = new Idempotency();

    @Data
    public static class Listener {
        private boolean enabled = true;
    }

    @Data
    public static class Idempotency {
        //  bật/tắt dedup
        private boolean enabled = false;
        // TTL cho eventId đã xử lý
        private Duration ttl = Duration.ofHours(24);

        /**
         * TTL cho trạng thái "đang xử lý" (PROCESSING). Nên ngắn hơn {@link #ttl}.
         * Nếu app crash giữa chừng, lease hết hạn thì message có thể được retry lại.
         */
        private Duration processingTtl = Duration.ofMinutes(5);
        // tránh memory phình vô hạn
        private int maxEntries = 500_000;
        // mỗi bao lâu quét xoá expired
        private Duration cleanupInterval = Duration.ofMinutes(5);

        //auto: ưu tiên Redis nếu có StringRedisTemplate, fallback memory
        //redis: bắt buộc dùng Redis (nếu không có redis class sẽ fail bean)
        //memory: luôn dùng in-memory
        private String store = "auto";
        private Redis redis = new Redis();

        private String keyPrefix = "lsf:idemp:";

        @Data
        public static class Redis {
            //cho phép tắt redis store dù có dependency
            private boolean enabled = true;
            //prefix key trong redis *
            private String keyPrefix = "lsf:idemp:";
        }

        //nếu true: auto mà không có Redis thì fail startup
        private boolean requireRedis = false;
    }
}
