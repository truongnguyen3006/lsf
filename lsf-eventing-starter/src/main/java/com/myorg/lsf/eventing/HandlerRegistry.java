package com.myorg.lsf.eventing;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
//Một cuốn danh bạ (dùng ConcurrentHashMap)
//lưu trữ mapping giữa "Tên sự kiện" và "Hàm xử lý sự kiện đó".
public class HandlerRegistry {
    private final Map<String, HandlerMethodInvoker> handlers = new ConcurrentHashMap<>();

    public void register(String eventType, HandlerMethodInvoker invoker) {
        handlers.put(eventType, invoker);
    }

    public HandlerMethodInvoker get(String eventType) {
        return handlers.get(eventType);
    }
}
