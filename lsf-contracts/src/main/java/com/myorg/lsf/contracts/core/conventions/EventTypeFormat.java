package com.myorg.lsf.contracts.core.conventions;

public class EventTypeFormat {
    private EventTypeFormat() {}

    // dùng: <domain>.<entity>.<action>.v<major>
    // ví dụ: ecommerce.order.placed.v1
    public static final String RECOMMENDED_PATTERN = "<domain>.<entity>.<action>.v<major>";
}
