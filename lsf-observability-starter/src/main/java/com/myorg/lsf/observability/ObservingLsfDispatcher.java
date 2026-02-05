package com.myorg.lsf.observability;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.LsfDispatcher;
import com.myorg.lsf.eventing.context.LsfDispatchOutcome;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;

@RequiredArgsConstructor
public class ObservingLsfDispatcher implements LsfDispatcher {

    private final LsfDispatcher delegate;
    private final LsfObservabilityProperties props;
    private final LsfMetrics metrics; // can be null if metrics disabled

    @Override
    public void dispatch(EventEnvelope env) {
        if (props.isMdcEnabled()) {
            putMdc(env);
        }

        Timer.Sample sample = (metrics != null && props.isMetricsEnabled())
                ? metrics.startTimer()
                : null;

        try {
            delegate.dispatch(env);

            if (metrics != null && props.isMetricsEnabled()) {
                String outcome = LsfDispatchOutcome.consume();
                if (outcome == null) outcome = "success";

                switch (outcome) {
                    case LsfDispatchOutcome.DUPLICATE -> metrics.incDuplicate();
                    case LsfDispatchOutcome.IN_FLIGHT -> metrics.incInFlight();
                    default -> metrics.incHandledSuccess();
                }

                metrics.stopTimer(sample, env, outcome);
            }
        } catch (RuntimeException e) {
            if (metrics != null && props.isMetricsEnabled()) {
                metrics.incHandledFail();
                metrics.stopTimer(sample, env, "fail");
            }
            throw e;
        } finally {
            // safety net
            LsfDispatchOutcome.clear();
            if (props.isMdcEnabled()) {
                clearMdc();
            }
        }
    }

    private void putMdc(EventEnvelope env) {
        if (env == null) return;
        if (env.getCorrelationId() != null) MDC.put("corrId", env.getCorrelationId());
        if (env.getEventId() != null) MDC.put("eventId", env.getEventId());
        if (env.getEventType() != null) MDC.put("eventType", env.getEventType());
    }

    private void clearMdc() {
        MDC.remove("corrId");
        MDC.remove("eventId");
        MDC.remove("eventType");
    }
}
