package com.myorg.lsf.kafka;

import com.myorg.lsf.contracts.core.exception.LsfNonRetryableException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.support.serializer.DeserializationException;
//nếu lỗi do không thể đọc định dạng (Serialization/Deserialization)
// thì không cần retry (vì có thử lại tỷ lần dữ liệu vẫn sai định dạng), đưa thẳng vào DLQ.
// Nếu lỗi do Framework định nghĩa (LsfNonRetryableException),
// cũng đưa vào DLQ. Các lỗi khác thì báo hết số lần retry.
public class DefaultLsfDlqReasonClassifier implements LsfDlqReasonClassifier {

    @Override
    public Decision classify(ConsumerRecord<?, ?> record, Exception ex) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        if (root == null) root = ex;

        // 1) Serialization/Deserialization => non-retryable
        if (root instanceof DeserializationException || root instanceof SerializationException) {
            return new Decision(LsfDlqReason.DESERIALIZATION.code(), true);
        }

        // 2) "Framework-level non-retryable" => reason from exception
        if (root instanceof LsfNonRetryableException nre) {
            String reason = nre.getReason();
            return new Decision(reason, true);
        }

        // 3) Default: DLQ after retry exhausted
        return new Decision(LsfDlqReason.RETRY_EXHAUSTED.code(), false);
    }
}
