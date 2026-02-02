package com.myorg.lsf.eventing;

import java.util.concurrent.CompletableFuture;

public interface LsfPublisher {
    CompletableFuture<?> publish(String topic, String key, String eventType,  String aggregateId, Object payload);
}
