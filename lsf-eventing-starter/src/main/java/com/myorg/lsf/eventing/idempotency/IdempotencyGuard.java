package com.myorg.lsf.eventing.idempotency;

import com.myorg.lsf.eventing.LsfEventingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
//Một bảo vệ lúc khởi động. Nếu cấu hình chạy Production mà cấu hình store=auto
// nhưng quên cài Redis, nó sẽ ném lỗi cấm ứng dụng khởi động (throw new IllegalStateException)
// để tránh thảm hoạ luỹ đẳng.
@Slf4j
@RequiredArgsConstructor
public class IdempotencyGuard implements ApplicationListener<ApplicationReadyEvent> {
    private final LsfEventingProperties props;
    private final Environment env;
    private final ApplicationContext ctx;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        var idem = props.getIdempotency();
        if (!idem.isEnabled()) return;

        String store = idem.getStore() == null ? "auto" : idem.getStore().toLowerCase();
        boolean hasRedis = false;
        try {
            if (ClassUtils.isPresent("org.springframework.data.redis.connection.RedisConnectionFactory", ctx.getClassLoader())) {
                Class<?> cfType = ClassUtils.forName("org.springframework.data.redis.connection.RedisConnectionFactory", ctx.getClassLoader());
                hasRedis = ctx.getBeanNamesForType(cfType, false, false).length > 0;
            }
        } catch (Throwable ignored) {
            hasRedis = false;
        }

        boolean isProd = false;
        for (String p : env.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)) {
                isProd = true;
                break;
            }
        }

        // store=redis thì đã fail ở config khác nếu thiếu Redis -> không xử lý ở đây
        if ("redis".equals(store)) return;

        // store=auto: nếu thiếu Redis -> warn hoặc fail
        if ("auto".equals(store) && !hasRedis) {
            String msg = "Idempotency store=auto nhưng KHÔNG có Redis -> fallback memory (chỉ dedup trong 1 instance).";
            if (idem.isRequireRedis() || isProd) {
                throw new IllegalStateException(msg + " (prod/requireRedis => fail startup)");
            }
            log.warn(msg + " (dev => warn)");
        }
    }
}
