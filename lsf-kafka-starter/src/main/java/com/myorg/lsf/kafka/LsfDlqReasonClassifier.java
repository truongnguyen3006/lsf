package com.myorg.lsf.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
//Phân loại xem một lỗi có đáng để thử lại (retry) hay không.

public interface LsfDlqReasonClassifier {

    record Decision(String reason, boolean nonRetryable) {}

    Decision classify(ConsumerRecord<?, ?> record, Exception ex);
}
