package com.myorg.lsf.kafka;

import com.myorg.lsf.contracts.core.exception.LsfNonRetryableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@AutoConfiguration(before = org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class)
@ConditionalOnClass(DefaultErrorHandler.class)
@EnableConfigurationProperties(KafkaProperties.class)
@Slf4j
public class KafkaErrorHandlingAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "lsf.kafka.dlq", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public DeadLetterPublishingRecoverer dlqRecover(
            KafkaProperties props,
            ObjectProvider<KafkaTemplate<String, Object>> templateProvider
    ) {
        KafkaTemplate<String, Object> template = templateProvider.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException(
                    "lsf.kafka.dlq.enabled=true nhưng không tìm thấy KafkaTemplate<String,Object>. " +
                            "Hãy đảm bảo lsf-kafka-starter producer auto-config được load hoặc app có cấu hình producer."
            );
        }

        return new DeadLetterPublishingRecoverer(
                template,
                (rec, ex) -> new TopicPartition(rec.topic() + props.getDlq().getSuffix(), rec.partition())
        );
    }


    /**
     * Pre-register the base meters so /actuator/metrics/lsf.kafka.* never returns 404
     * even before the first retry/DLQ happens.
     */
    @Bean
    public ApplicationRunner lsfKafkaMetricsPreregister(ObjectProvider<MeterRegistry> registryProvider) {
        return args -> {
            MeterRegistry reg = registryProvider.getIfAvailable();
            if (reg == null) return;
            Counter.builder("lsf.kafka.retry").register(reg);
            Counter.builder("lsf.kafka.dlq").register(reg);
            Counter.builder("lsf.kafka.recovery_failed").register(reg);
        };
    }

    /**
     * Attach our metrics-aware RetryListener to ANY DefaultErrorHandler bean,
     * including ones coming from other starters.
     */
    @Bean
    public static BeanPostProcessor lsfKafkaErrorHandlerMetricsPostProcessor(
            Environment env,
            ObjectProvider<MeterRegistry> registryProvider
    ) {
        return new DefaultErrorHandlerMetricsPostProcessor(env, registryProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public CommonErrorHandler commonErrorHandler(
            KafkaProperties props,
            ObjectProvider<DeadLetterPublishingRecoverer> recovererProvider
    ) {
        long interval = props.getConsumer().getRetry().getBackoff().toMillis();
        long attempts = props.getConsumer().getRetry().getAttempts();
        var backoff = new FixedBackOff(interval, attempts);

        DeadLetterPublishingRecoverer recoverer = recovererProvider.getIfAvailable();

        DefaultErrorHandler handler = (props.getDlq().isEnabled() && recoverer != null)
                ? new DefaultErrorHandler(recoverer, backoff)
                : new DefaultErrorHandler(backoff);

        handler.setCommitRecovered(true);

        handler.addNotRetryableExceptions(
                SerializationException.class,
                DeserializationException.class,
                LsfNonRetryableException.class
        );

        return handler;
    }


    @Slf4j
    static class DefaultErrorHandlerMetricsPostProcessor implements BeanPostProcessor {

        private final String service;
        private final ObjectProvider<MeterRegistry> registryProvider;

        DefaultErrorHandlerMetricsPostProcessor(Environment env, ObjectProvider<MeterRegistry> registryProvider) {
            this.service = env.getProperty("spring.application.name", "unknown-service");
            this.registryProvider = registryProvider;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof DefaultErrorHandler handler) {
                attachIfMissing(handler);
                return bean;
            }

            // Also cover the case where a module configures the error handler directly on the container factory
            // instead of exposing it as a bean.
            if (bean instanceof org.springframework.kafka.config.AbstractKafkaListenerContainerFactory<?, ?, ?> factory) {
                DefaultErrorHandler deh = extractDefaultErrorHandlerFromFactory(factory);
                if (deh != null) {
                    attachIfMissing(deh);
                }
            }

            return bean;
        }

        private void attachIfMissing(DefaultErrorHandler handler) {
            RetryListener listener = new LsfKafkaRetryDlqMetricsListener(service, registryProvider);

            if (hasListener(handler, LsfKafkaRetryDlqMetricsListener.class)) {
                return;
            }

            // Prefer addRetryListener if it exists (keeps other listeners intact)
            try {
                Method add = handler.getClass().getMethod("addRetryListener", RetryListener.class);
                add.invoke(handler, listener);
                log.debug("Attached LSF RetryListener via addRetryListener to {}", handler.getClass().getName());
                return;
            } catch (NoSuchMethodException ignored) {
                // fallback below
            } catch (Exception e) {
                log.warn("Failed to attach RetryListener via addRetryListener; will fallback to setRetryListeners: {}", e.toString());
            }

            // Fallback: try to append to existing listeners and call setRetryListeners
            List<RetryListener> existing = getExistingListeners(handler);
            existing.add(listener);

            try {
                Method set = handler.getClass().getMethod("setRetryListeners", RetryListener[].class);
                set.invoke(handler, (Object) existing.toArray(new RetryListener[0]));
                log.debug("Attached LSF RetryListener via setRetryListeners to {}", handler.getClass().getName());
            } catch (Exception e) {
                // last resort: do nothing (no metrics)
                log.warn("Failed to attach RetryListener to DefaultErrorHandler: {}", e.toString());
            }
        }

        private static boolean hasListener(DefaultErrorHandler handler, Class<?> listenerClass) {
            for (RetryListener l : getExistingListeners(handler)) {
                if (listenerClass.isInstance(l)) return true;
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        private static List<RetryListener> getExistingListeners(DefaultErrorHandler handler) {
            // 1) try a public getter if it exists
            try {
                Method m = handler.getClass().getMethod("getRetryListeners");
                Object res = m.invoke(handler);
                if (res instanceof RetryListener[] arr) {
                    return new ArrayList<>(List.of(arr));
                }
                if (res instanceof Collection<?> col) {
                    List<RetryListener> out = new ArrayList<>();
                    for (Object o : col) {
                        if (o instanceof RetryListener rl) out.add(rl);
                    }
                    return out;
                }
            } catch (NoSuchMethodException ignored) {
                // fallback
            } catch (Exception ignored) {
                // fallback
            }

            // 2) try to access a field commonly used by Spring Kafka
            try {
                Field f = handler.getClass().getDeclaredField("retryListeners");
                f.setAccessible(true);
                Object res = f.get(handler);
                if (res instanceof RetryListener[] arr) {
                    return new ArrayList<>(List.of(arr));
                }
                if (res instanceof Collection<?> col) {
                    List<RetryListener> out = new ArrayList<>();
                    for (Object o : col) {
                        if (o instanceof RetryListener rl) out.add(rl);
                    }
                    return out;
                }
            } catch (Exception ignored) {
                // ignore
            }

            return new ArrayList<>();
        }
        private static DefaultErrorHandler extractDefaultErrorHandlerFromFactory(Object factory) {
            Object eh = invokeNoArgIfExists(factory, "getCommonErrorHandler");
            if (eh == null) eh = invokeNoArgIfExists(factory, "getErrorHandler");
            if (eh == null) eh = readFieldIfExists(factory, "commonErrorHandler");
            if (eh == null) eh = readFieldIfExists(factory, "errorHandler");

            return (eh instanceof DefaultErrorHandler deh) ? deh : null;
        }

        private static Object invokeNoArgIfExists(Object target, String methodName) {
            Class<?> c = target.getClass();
            while (c != null && c != Object.class) {
                try {
                    Method m = c.getDeclaredMethod(methodName);
                    m.setAccessible(true);
                    return m.invoke(target);
                } catch (NoSuchMethodException ignored) {
                    // try parent
                } catch (Exception ignored) {
                    return null;
                }
                c = c.getSuperclass();
            }
            return null;
        }

        private static Object readFieldIfExists(Object target, String fieldName) {
            Class<?> c = target.getClass();
            while (c != null && c != Object.class) {
                try {
                    Field f = c.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f.get(target);
                } catch (NoSuchFieldException ignored) {
                    // try parent
                } catch (Exception ignored) {
                    return null;
                }
                c = c.getSuperclass();
            }
            return null;
        }

    }

    /**
     * Micrometer metrics for retry/DLQ.
     * We only increment tagged meters; the base meters are pre-registered at 0 to avoid 404.
     */
    @Slf4j
    static class LsfKafkaRetryDlqMetricsListener implements RetryListener {

        private final String service;
        private final ObjectProvider<MeterRegistry> registryProvider;

        LsfKafkaRetryDlqMetricsListener(String service, ObjectProvider<MeterRegistry> registryProvider) {
            this.service = service;
            this.registryProvider = registryProvider;
        }

        @Override
        public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
            log.warn("Retrying topic={} partition={} offset={} attempt={} error={}"
                    , record.topic(), record.partition(), record.offset(), deliveryAttempt, ex.toString());
            inc("lsf.kafka.retry", record.topic(), ex);
        }

        @Override
        public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
            log.error("Recovered (sent to DLQ) topic={} partition={} offset={} error={}"
                    , record.topic(), record.partition(), record.offset(), ex.toString());
            inc("lsf.kafka.dlq", record.topic(), ex);
        }

        @Override
        public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
            log.error("Recovery FAILED topic={} partition={} offset={} originalError={} recoveryError={}" 
                    , record.topic(), record.partition(), record.offset(), original.toString(), failure.toString(), failure);
            inc("lsf.kafka.recovery_failed", record.topic(), failure);
        }

        private void inc(String metric, String topic, Exception ex) {
            MeterRegistry registry = registryProvider.getIfAvailable();
            if (registry == null) return;

            Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
            String exName = (root != null ? root.getClass().getSimpleName() : ex.getClass().getSimpleName());

            Counter.builder(metric)
                    .tag("service", service)
                    .tag("topic", topic)
                    .tag("exception", exName)
                    .register(registry)
                    .increment();
        }
    }
}
