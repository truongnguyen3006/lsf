package com.myorg.lsf.observability;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LsfMetrics {
    public record Sample(Timer.Sample sample) {}

    private final MeterRegistry registry;
    private final String serviceName;
    private final LsfObservabilityProperties props;

    public Sample start() {
        return new Sample(Timer.start(registry));
    }

    public void stop(Sample sample, EventEnvelope env, String outcome) {
        if (sample == null) return;
        Timer.Builder b = Timer.builder("lsf.event.processing")
                .tag("service", serviceName)
                .tag("outcome", outcome);

        if (props.isTagEventType() && env.getEventType() != null) b.tag("eventType", env.getEventType());
        sample.sample().stop(b.register(registry));
    }

    public void countHandled(EventEnvelope env) {
        counter("lsf.event.handled", env).increment();
    }

    public void countFailed(EventEnvelope env) {
        counter("lsf.event.failed", env).increment();
    }

    private Counter counter(String name, EventEnvelope env) {
        Counter.Builder b = Counter.builder(name).tag("service", serviceName);
        if (props.isTagEventType() && env.getEventType() != null) b.tag("eventType", env.getEventType());
        return b.register(registry);
    }
}
