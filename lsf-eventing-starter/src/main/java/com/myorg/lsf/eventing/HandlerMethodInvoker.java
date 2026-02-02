package com.myorg.lsf.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.lang.reflect.Method;

@Data
@AllArgsConstructor
public class HandlerMethodInvoker {
    private final Object target;
    private final Method method;
    private final Class<?> payloadClass;
    private final ObjectMapper mapper;

    public void invoke(EventEnvelope env) {
        try {
            Object payloadObj = mapper.treeToValue(env.getPayload(), payloadClass);

            // Hỗ trợ 2 signature:
            // 1) handle(Payload p)
            // 2) handle(EventEnvelope env, Payload p)
            if (method.getParameterCount() == 1) {
                method.invoke(target, payloadObj);
                return;
            }
            if (method.getParameterCount() == 2) {
                method.invoke(target, env, payloadObj);
                return;
            }
            throw new IllegalStateException("Handler method must have 1 or 2 params: (payload) or (envelope,payload)");
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke handler: " + method, e);
        }
    }
}
