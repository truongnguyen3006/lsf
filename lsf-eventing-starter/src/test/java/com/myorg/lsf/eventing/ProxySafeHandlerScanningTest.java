package com.myorg.lsf.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Commit 1: Ensure handler scanning works even when handler beans are proxied.
 *
 * In real systems, beans are often proxied (AOP, @Transactional, tracing, etc.).
 * If the framework scans annotations on bean.getClass(), it will miss
 * method annotations declared on the target class.
 */
class ProxySafeHandlerScanningTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    com.myorg.lsf.eventing.autoconfig.LsfEventingAutoConfiguration.class
            ))
            // Không test Kafka listener ở đây (để unit test nhẹ & ổn định)
            .withPropertyValues("lsf.eventing.listener.enabled=false");

    @Test
    void shouldRegisterHandlerEvenWhenBeanIsProxied() {
        runner.withUserConfiguration(TestConfig.class)
                .run(ctx -> {
                    HandlerRegistry registry = ctx.getBean(HandlerRegistry.class);

                    assertThat(registry.get("demo.created"))
                            .as("handler for demo.created should be registered")
                            .isNotNull();
                });
    }

    @Configuration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        /**
         * Tránh auto tạo DefaultLsfPublisher (nó cần KafkaTemplate bean).
         * Ta stub để context chạy được mà không cần Kafka.
         */
        @Bean
        LsfPublisher stubPublisher() {
            return (topic, key, eventType, aggregateId, payload) -> CompletableFuture.completedFuture(null);
        }

        /**
         * PostProcessor này sẽ proxy DemoHandler sau khi init,
         * mô phỏng tình huống bean bị proxy trong production.
         */
        @Bean
        static BeanPostProcessor proxyingPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof DemoHandler) {
                        ProxyFactory pf = new ProxyFactory(bean);
                        pf.setProxyTargetClass(true); // CGLIB proxy
                        pf.addAdvice((MethodInterceptor) invocation -> invocation.proceed()); // no-op advice
                        return pf.getProxy();
                    }
                    return bean;
                }
            };
        }

        @Component
        static class DemoHandler {
            @LsfEventHandler(value = "demo.created", payload = DemoPayload.class)
            public void handle(DemoPayload payload) {
                // no-op
            }
        }

        static class DemoPayload {
            public String id;
        }
    }
}
