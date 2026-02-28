package com.myorg.lsf.eventing;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import com.myorg.lsf.eventing.context.LsfDispatchOutcome;
import com.myorg.lsf.eventing.idempotency.IdempotencyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
//Sử dụng Design Pattern "Decorator". Nó bọc bên ngoài DefaultLsfDispatcher.
// Nhận message -> Hỏi Store xem eventId này đã xử lý chưa -> Trùng thì vứt đi,
// Đang xử lý thì bỏ qua,
// Chưa xử lý thì ghi nhận trạng thái
// và cho phép DefaultLsfDispatcher chạy tiếp.
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

        IdempotencyStore.Lease lease = store.tryBeginProcessing(eventId);

        if (lease.decision() == IdempotencyStore.Decision.DUPLICATE) {
            LsfDispatchOutcome.markDuplicate();
            log.info("Skip duplicate eventId={} eventType={}", eventId, env.getEventType());
            return;
        }

        if (lease.decision() == IdempotencyStore.Decision.IN_FLIGHT) {
            LsfDispatchOutcome.markInFlight();
            log.info("Skip in-flight eventId={} eventType={}", eventId, env.getEventType());
            return;
        }

        try {
            delegate.dispatch(env);
            store.markDone(eventId, lease.token());
        } catch (RuntimeException e) {
            try {
                store.releaseProcessing(eventId, lease.token());
            } catch (Exception ex) {
                log.debug("Failed to release processing lease eventId={} after failure", eventId, ex);
            }
            throw e;
        }
    }
}
