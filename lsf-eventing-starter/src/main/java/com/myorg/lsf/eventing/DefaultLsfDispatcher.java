package com.myorg.lsf.eventing;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.exception.UnknownEventTypeException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@Data
@AllArgsConstructor
public class DefaultLsfDispatcher implements LsfDispatcher {
    private static final Logger log = LoggerFactory.getLogger(DefaultLsfDispatcher.class);

    private final HandlerRegistry registry;
    private final boolean ignoreUnknown;

    @Override
    public void dispatch(EventEnvelope env) {
        String type = env.getEventType();
        HandlerMethodInvoker invoker = registry.get(type);

        if (invoker == null) {
            if (ignoreUnknown) {
                log.warn("No handler for eventType={}, eventId={}", type, env.getEventId());
                return;
            }
            throw new UnknownEventTypeException(type, env.getEventId());
        }

        invoker.invoke(env);
    }
}
