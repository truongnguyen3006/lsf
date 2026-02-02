package com.demo.app;

import com.myorg.lsf.kafka.KafkaProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Ensure demo topics exist (useful when the broker disables auto topic creation).
 * Requires KafkaAdmin bean (provided by lsf-kafka-starter).
 */
@Configuration
public class DemoTopicsConfiguration {

    @Bean
    public NewTopic demoTopic() {
        return TopicBuilder.name("demo-topic")
                .partitions(2)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic demoTopicDlq(KafkaProperties props) {
        String dlqName = "demo-topic" + props.getDlq().getSuffix();
        return TopicBuilder.name(dlqName)
                .partitions(2)
                .replicas(1)
                .build();
    }
}
