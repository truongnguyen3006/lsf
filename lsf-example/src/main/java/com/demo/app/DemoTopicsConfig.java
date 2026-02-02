package com.demo.app;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

public class DemoTopicsConfig {
    @Bean
    public NewTopic demoTopic() {
        return TopicBuilder.name("demo-topic").partitions(2).replicas(1).build();
    }

    @Bean
    public NewTopic demoTopicDlq() {
        return TopicBuilder.name("demo-topic.DLQ").partitions(2).replicas(1).build();
    }
}
