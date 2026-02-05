package com.myorg.lsf.observability;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;

@RequiredArgsConstructor
public class LsfMetrics {

    private final MeterRegistry registry;
    private final String serviceName;
    private final LsfObservabilityProperties props;

    // Pre-created base meters (so actuator never 404)
    private Counter cHandledSuccess;
    private Counter cHandledFail;
    private Counter cRetry;
    private Counter cDlq;
    private Counter cDuplicate;
    private Counter cInFlight;

    /** Call once on startup. */
    public void preRegisterBaseMeters() {
        cHandledSuccess = Counter.builder("lsf.event.handled.success").tag("service", serviceName).register(registry);
        cHandledFail    = Counter.builder("lsf.event.handled.fail").tag("service", serviceName).register(registry);
        cRetry          = Counter.builder("lsf.event.retry").tag("service", serviceName).register(registry);
        cDlq            = Counter.builder("lsf.event.dlq").tag("service", serviceName).register(registry);
        cDuplicate      = Counter.builder("lsf.event.duplicate").tag("service", serviceName).register(registry);
        cInFlight       = Counter.builder("lsf.event.in_flight").tag("service", serviceName).register(registry);

        // ✅ backward-compatible aliases (để test cũ gọi lsf.kafka.dlq không 404)
        Counter.builder("lsf.kafka.retry").tag("service", serviceName).register(registry);
        Counter.builder("lsf.kafka.dlq").tag("service", serviceName).register(registry);

        // Ensure timer name exists
        Timer.builder("lsf.event.processing").tag("service", serviceName).register(registry);
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTimer(Timer.Sample sample, EventEnvelope env, String outcome) {
        if (sample == null) return;

        Timer.Builder b = Timer.builder("lsf.event.processing")
                .tag("service", serviceName);

        if (props.isTagOutcome()) b.tag("outcome", outcome);
        if (props.isTagEventType() && env != null && env.getEventType() != null) b.tag("eventType", env.getEventType());

        if (props.isTagTopic()) {
            String topic = MDC.get("topic");
            if (topic != null && !topic.isBlank()) b.tag("topic", topic);
        }

        sample.stop(b.register(registry));
    }

    public void incHandledSuccess() { if (cHandledSuccess != null) cHandledSuccess.increment(); }
    public void incHandledFail()    { if (cHandledFail != null) cHandledFail.increment(); }
    public void incDuplicate()      { if (cDuplicate != null) cDuplicate.increment(); }
    public void incInFlight()       { if (cInFlight != null) cInFlight.increment(); }

    /** These two should be called by Kafka error handler when retry/dlq happens. */
    public void incRetry() {
        if (cRetry != null) cRetry.increment();
        registry.counter("lsf.kafka.retry", "service", serviceName).increment(); // alias
    }

    public void incDlq() {
        if (cDlq != null) cDlq.increment();
        registry.counter("lsf.kafka.dlq", "service", serviceName).increment(); // alias
    }
}
