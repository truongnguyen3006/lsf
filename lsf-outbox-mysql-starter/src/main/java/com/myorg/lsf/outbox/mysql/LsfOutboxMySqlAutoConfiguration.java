package com.myorg.lsf.outbox.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
@EnableConfigurationProperties(LsfOutboxMySqlProperties.class)
public class LsfOutboxMySqlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper lsfOutboxObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "lsf.outbox", name = "enabled", havingValue = "true")
    public OutboxWriter outboxWriter(JdbcTemplate jdbcTemplate,
                                     ObjectMapper mapper,
                                     LsfOutboxMySqlProperties props) {
        return new JdbcOutboxWriter(jdbcTemplate, mapper, props);
    }
}
