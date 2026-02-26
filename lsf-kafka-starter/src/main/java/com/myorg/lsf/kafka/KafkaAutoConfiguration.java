package com.myorg.lsf.kafka;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;
//kích hoạt các file cấu hình khác và cung cấp các Bean cơ bản nhất.
//Spring Boot sẽ tự động import nó vào ApplicationContext khi starter có mặt trên classpath.
@AutoConfiguration
//tạo bean LsfKafkaProperties và bind các giá trị từ application.yml theo prefix lsf.kafka vào nó.
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaAutoConfiguration {
    // Marker bean để test starter đã được auto-config vào context hay chưa
    //kiểm tra binding của properties có đúng không
    public record LsfKafkaMarker(String bootstrapServers, String schemaRegistryUrl) {}

    //(Annotation) Chỉ tạo bean này nếu app chưa tự tạo bean cùng loại.Cung cấp default, nhưng không khóa tay app.
    @Bean
    @ConditionalOnMissingBean
    public LsfKafkaMarker lsfKafkaMarker(KafkaProperties props) {
        return new LsfKafkaMarker(props.getBootstrapServers(), props.getSchemaRegistryUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    public SerdeFactory serdeFactory(KafkaProperties props) {
        return new SerdeFactory(props.getSchemaRegistryUrl());
    }

    /**
     * Provide KafkaAdmin wired to lsf.kafka.bootstrap-servers so NewTopic beans can be applied.
     * (Spring Boot's default KafkaAdmin looks at spring.kafka.bootstrap-servers, which we don't use.)
     */
    @Bean
    @ConditionalOnMissingBean
    public KafkaAdmin kafkaAdmin(KafkaProperties props) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        return new KafkaAdmin(cfg);
    }
}
