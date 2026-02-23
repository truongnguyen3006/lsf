package com.myorg.lsf.outbox.mysql;

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
        // Counters
        published = Counter.builder("outbox.published").register(registry);
        failed = Counter.builder("outbox.failed").register(registry);

        // Gauge (pending)
        registry.gauge("outbox.pending", repo, r -> r.countPending(clock.instant()));
    }

    public void incPublished() {
        if (published != null) published.increment();
    }

    public void incFailed() {
        if (failed != null) failed.increment();
    }
}
