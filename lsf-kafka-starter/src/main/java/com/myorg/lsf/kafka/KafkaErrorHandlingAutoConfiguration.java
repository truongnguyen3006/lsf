package com.myorg.lsf.kafka;

import com.myorg.lsf.contracts.core.exception.LsfNonRetryableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.beans.factory.ObjectProvider;
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

@AutoConfiguration
@ConditionalOnClass(DefaultErrorHandler.class)
@EnableConfigurationProperties(KafkaProperties.class)
@Slf4j
public class KafkaErrorHandlingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public DeadLetterPublishingRecoverer dlqRecover(KafkaProperties props, KafkaTemplate<String, Object> template) {
        return new DeadLetterPublishingRecoverer(
                template,
                (rec, ex) -> new TopicPartition(rec.topic() + props.getDlq().getSuffix()
                , rec.partition())
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public CommonErrorHandler commonErrorHandler(
            KafkaProperties props,
            DeadLetterPublishingRecoverer recoverer,
            Environment env,
            ObjectProvider<MeterRegistry> registryProvider
    ) {
        String service = env.getProperty("spring.application.name", "unknown-service");
        MeterRegistry reg = registryProvider.getIfAvailable();

        Counter retry = (reg == null) ? null : Counter.builder("lsf.kafka.retry").register(reg);
        Counter dlq   = (reg == null) ? null : Counter.builder("lsf.kafka.dlq").register(reg);
        Counter recFail = (reg == null) ? null : Counter.builder("lsf.kafka.recovery_failed").register(reg);

        long interval = props.getConsumer().getRetry().getBackoff().toMillis();
        long attempts = props.getConsumer().getRetry().getAttempts();
        var backoff = new FixedBackOff(interval, attempts);

        DefaultErrorHandler handler = props.getDlq().isEnabled()
                ? new DefaultErrorHandler(recoverer, backoff)
                : new DefaultErrorHandler(backoff);

        handler.setCommitRecovered(true);

        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                if (retry != null) retry.increment();
                log.warn("Retrying topic={} partition={} offset={} attempt={} error={}",
                        record.topic(), record.partition(), record.offset(), deliveryAttempt, ex.toString());

                inc(reg, "lsf.kafka.retry", service, record.topic(), ex);
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
                if (dlq != null) dlq.increment();
                log.error("Recovered (sent to DLQ) topic={} partition={} offset={} error={}",
                        record.topic(), record.partition(), record.offset(), ex.toString());

                inc(reg, "lsf.kafka.dlq", service, record.topic(), ex);
            }

            @Override
            public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
                if (recFail != null) recFail.increment();
                log.error("Recovery FAILED topic={} partition={} offset={} originalError={} recoveryError={}",
                        record.topic(), record.partition(), record.offset(),
                        original.toString(), failure.toString(), failure);

                inc(reg, "lsf.kafka.recovery_failed", service, record.topic(), failure);
            }
        });

        handler.addNotRetryableExceptions(
                SerializationException.class,
                DeserializationException.class,
                LsfNonRetryableException.class
        );

        return handler;
    }

    private static void inc(MeterRegistry registry, String metric, String service, String topic, Exception ex) {
        if (registry == null) return;

        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        String exName = (root != null ? root.getClass().getSimpleName()
                : ex.getClass().getSimpleName());

        Counter.builder(metric)
                .tag("service", service)
                .tag("topic", topic)
                .tag("exception", exName)
                .register(registry)
                .increment();
    }


}
