package com.myorg.lsf.outbox.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import com.myorg.lsf.outbox.OutboxWriter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
@EnableConfigurationProperties(LsfOutboxMySqlProperties.class)
public class LsfOutboxMySqlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "lsfOutboxObjectMapper")
    public ObjectMapper lsfOutboxObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock lsfOutboxClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcOutboxRepository jdbcOutboxRepository(JdbcTemplate jdbc, LsfOutboxMySqlProperties props) {
        return new JdbcOutboxRepository(jdbc, props);
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionTemplate lsfOutboxTxTemplate(PlatformTransactionManager txManager) {
        return new TransactionTemplate(txManager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "lsf.outbox", name = "enabled", havingValue = "true")
    public OutboxWriter outboxWriter(JdbcTemplate jdbcTemplate,
                                     ObjectMapper lsfOutboxObjectMapper,
                                     LsfOutboxMySqlProperties props) {
        return new JdbcOutboxWriter(jdbcTemplate, lsfOutboxObjectMapper, props);
    }

    @Bean(name = "lsfOutboxSchedule")
    public LsfOutboxScheduleValues lsfOutboxScheduleValues(LsfOutboxMySqlProperties props) {
        return new LsfOutboxScheduleValues(props);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisherHooks outboxPublisherHooks() {
        return new OutboxPublisherHooks() {};
    }

    @Bean
    @ConditionalOnProperty(prefix = "lsf.outbox.publisher", name = "enabled", havingValue = "true")
    @ConditionalOnBean(KafkaTemplate.class)
    public OutboxMetrics outboxMetrics(ObjectProvider<MeterRegistry> regProvider,
                                       JdbcOutboxRepository repo,
                                       Clock clock,
                                       LsfOutboxMySqlProperties props) {
        MeterRegistry reg = regProvider.getIfAvailable();
        if (reg == null || !props.getMetrics().isEnabled()) return null;

        OutboxMetrics m = new OutboxMetrics(reg, repo, clock);
        m.preRegister(); // ensure meters exist at boot
        return m;
    }

    @Bean
    @ConditionalOnProperty(prefix = "lsf.outbox.publisher", name = "enabled", havingValue = "true")
    @ConditionalOnBean(KafkaTemplate.class)
    public OutboxPublisher outboxPublisher(LsfOutboxMySqlProperties props,
                                           JdbcOutboxRepository repo,
                                           KafkaTemplate<String, Object> kafkaTemplate,
                                           ObjectMapper lsfOutboxObjectMapper,
                                           TransactionTemplate lsfOutboxTxTemplate,
                                           Clock lsfOutboxClock,
                                           OutboxPublisherHooks hooks,
                                           ObjectProvider<OutboxMetrics> metricsProvider) {
        return new OutboxPublisher(
                props,
                repo,
                kafkaTemplate,
                lsfOutboxObjectMapper,
                lsfOutboxTxTemplate,
                lsfOutboxClock,
                hooks,
                metricsProvider.getIfAvailable()
        );
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "lsf.outbox.publisher", name = "enabled", havingValue = "true")
    static class SchedulingConfig {}
}
