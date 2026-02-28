package com.myorg.lsf.eventing;

import java.util.concurrent.CompletableFuture;
//Giao diện để developer gửi sự kiện.
// Tự động bọc payload vào EventEnvelope (qua EnvelopeBuilder),
// đính kèm các Header quan trọng của Kafka (Event ID, Type, Correlation ID)
// để phục vụ cho việc tracking (truy vết) hệ thống.
public interface LsfPublisher {
    CompletableFuture<?> publish(String topic, String key, String eventType,  String aggregateId, Object payload);
}
