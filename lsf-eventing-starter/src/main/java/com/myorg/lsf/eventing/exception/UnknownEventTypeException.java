package com.myorg.lsf.eventing.exception;

import com.myorg.lsf.contracts.core.exception.LsfNonRetryableException;
//Lỗi không tìm thấy hàm xử lý (UnknownEventTypeException)
// kế thừa từ LsfNonRetryableException (từ thư viện core),
// báo hiệu cho Kafka Error Handler ném thẳng vào DLQ chứ không retry.
public class UnknownEventTypeException extends LsfNonRetryableException {
    public UnknownEventTypeException(String eventType, String eventId) {
        super("UNKNOWN_EVENT_TYPE", "No handler for eventType=" + eventType + ", eventId=" + eventId);
    }
}
