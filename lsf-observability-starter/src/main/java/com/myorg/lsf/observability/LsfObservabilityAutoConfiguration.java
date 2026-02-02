package com.myorg.lsf.observability;

import com.myorg.lsf.eventing.LsfDispatcher;
import com.myorg.lsf.observability.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnClass(LsfDispatcher.class)
@EnableConfigurationProperties(LsfObservabilityProperties.class)
public class LsfObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class) //  không có MeterRegistry thì không tạo bean này
    public LsfMetrics lsfMetrics(MeterRegistry registry, Environment env, LsfObservabilityProperties props) {
        String app = env.getProperty("spring.application.name", "unknown-service");
        return new LsfMetrics(registry, app, props);
    }

    @Bean
    public static org.springframework.beans.factory.config.BeanPostProcessor observingDispatcherBpp(
            LsfObservabilityProperties props,
            ObjectProvider<LsfMetrics> metricsProvider
    ) {
        return new org.springframework.beans.factory.config.BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!props.isEnabled()) return bean;
                if (!(bean instanceof LsfDispatcher d)) return bean;

                // metrics có thể null nếu app không có MeterRegistry
                LsfMetrics metrics = metricsProvider.getIfAvailable();
                return new ObservingLsfDispatcher(d, props, metrics);
            }
        };
    }
}