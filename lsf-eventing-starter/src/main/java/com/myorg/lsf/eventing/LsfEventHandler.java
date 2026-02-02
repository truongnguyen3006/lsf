package com.myorg.lsf.eventing;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LsfEventHandler {
    //eventType string, ví dụ EcommerceEventTypes.ORDER_PLACED_V1
    String value();
    //class của payload để auto convert
    Class<?> payload();
}
