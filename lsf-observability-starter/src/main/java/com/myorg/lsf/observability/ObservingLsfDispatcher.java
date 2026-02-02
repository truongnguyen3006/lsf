package com.myorg.lsf.observability;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfDispatcher;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;

@RequiredArgsConstructor
public class ObservingLsfDispatcher implements LsfDispatcher {

    private final LsfDispatcher delegate;
    private final LsfObservabilityProperties props;
    private final LsfMetrics metrics; // có thể null (optional)

    @Override
    public void dispatch(EventEnvelope env) {
        if (props.isMdcEnabled()) {
            putMdc(env);
        }

        LsfMetrics.Sample sample = (metrics != null && props.isMetricsEnabled())
                ? metrics.start()
                : null;

        try {
            delegate.dispatch(env);

            if (metrics != null && props.isMetricsEnabled()) {
                metrics.countHandled(env);
                metrics.stop(sample, env, "success");
            }
        } catch (RuntimeException e) {
            if (metrics != null && props.isMetricsEnabled()) {
                metrics.countFailed(env);
                metrics.stop(sample, env, "fail");
            }
            throw e;
        } finally {
            if (props.isMdcEnabled()) {
                clearMdc();
            }
        }
    }

    private void putMdc(EventEnvelope env) {
        if (env.getCorrelationId() != null) MDC.put("corrId", env.getCorrelationId());
        if (env.getEventId() != null) MDC.put("eventId", env.getEventId());
        if (env.getEventType() != null) MDC.put("eventType", env.getEventType());
        if (env.getProducer() != null) MDC.put("producer", env.getProducer());
    }

    private void clearMdc() {
        MDC.remove("corrId");
        MDC.remove("eventId");
        MDC.remove("eventType");
        MDC.remove("producer");
    }
}
