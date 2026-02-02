package com.myorg.lsf.eventing;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.idempotency.IdempotencyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class IdempotentLsfDispatcher implements LsfDispatcher {
    private final LsfDispatcher delegate;
    private final IdempotencyStore store;

    @Override
    public void dispatch(EventEnvelope env) {
        String eventId = env.getEventId();
        if (eventId == null || eventId.isBlank()) {
            delegate.dispatch(env);
            return;
        }

        // Acquire a short-lived processing lease (cross-instance safe when store supports it).
        IdempotencyStore.Lease lease = store.tryBeginProcessing(eventId);
        if (lease.decision() == IdempotencyStore.Decision.DUPLICATE) {
            log.info("Skip duplicate eventId={} eventType={}", eventId, env.getEventType());
            return;
        }
        if (lease.decision() == IdempotencyStore.Decision.IN_FLIGHT) {
            log.info("Skip in-flight eventId={} eventType={}", eventId, env.getEventType());
            return;
        }

        try {
            delegate.dispatch(env);
            // Mark DONE only after success.
            store.markDone(eventId, lease.token());
        } catch (RuntimeException e) {
            // Best-effort release so the message can be retried.
            try {
                store.releaseProcessing(eventId, lease.token());
            } catch (Exception ex) {
                log.debug("Failed to release processing lease eventId={} after handler failure", eventId, ex);
            }
            throw e;
        }
    }
}
