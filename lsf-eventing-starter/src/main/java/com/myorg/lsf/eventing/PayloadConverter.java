package com.myorg.lsf.eventing;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
//cố gắng convert mọi dạng dữ liệu (JsonNode, Map, String)
// thành EventEnvelope chuẩn để hệ thống có thể đọc hiểu.
public interface PayloadConverter {
    EventEnvelope toEnvelope(Object value);
}
