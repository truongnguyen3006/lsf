package com.myorg.lsf.eventing;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
//Annotation tự định nghĩa, dùng để gắn lên các hàm xử lý nghiệp vụ.
public @interface LsfEventHandler {
    //eventType string(tên event), ví dụ EcommerceEventTypes.ORDER_PLACED_V1
    String value();
    //class của payload để auto convert (kiểu data)
    Class<?> payload();
}
