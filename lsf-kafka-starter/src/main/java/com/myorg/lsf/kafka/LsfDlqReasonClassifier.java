package com.myorg.lsf.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

public interface LsfDlqReasonClassifier {

    record Decision(String reason, boolean nonRetryable) {}

    Decision classify(ConsumerRecord<?, ?> record, Exception ex);
}
