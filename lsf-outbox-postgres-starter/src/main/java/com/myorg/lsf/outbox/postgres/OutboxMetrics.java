package com.myorg.lsf.outbox.postgres;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

@RequiredArgsConstructor
public class OutboxMetrics {

    private final MeterRegistry registry;
    private final JdbcOutboxRepository repo;
    private final Clock clock;

    private Counter published;
    private Counter failed;

    public void preRegister() {
        published = Counter.builder("outbox.published").register(registry);
        failed = Counter.builder("outbox.failed").register(registry);

        registry.gauge("outbox.pending", repo, r -> r.countPending(clock.instant()));
    }

    public void incPublished() { if (published != null) published.increment(); }
    public void incFailed() { if (failed != null) failed.increment(); }
}