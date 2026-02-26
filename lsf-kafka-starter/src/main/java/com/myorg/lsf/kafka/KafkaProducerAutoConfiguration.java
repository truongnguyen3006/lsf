package com.myorg.lsf.kafka;


import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;
//Tạo ra các đối tượng để ứng dụng có thể gửi tin nhắn lên Kafka.
@AutoConfiguration(before = org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration.class)
//chỉ chạy nếu app có spring-kafka trên classpath.
//import thư viện spring-kafka gốc của Spring
@ConditionalOnClass(KafkaTemplate.class)
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaProducerAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    //Nạp các thông số từ KafkaProperties vào ProducerConfig của Kafka.
    public ProducerFactory<String, Object> producerFactory(KafkaProperties props, SerdeFactory serdeFactory) {
        Map<String, Object> p = new HashMap<>();
        //bootstrap.servers là danh sách broker (host:port)
        // để producer “bắt tay” lấy metadata cluster (partition, leader…).
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, props.getBootstrapServers());
        //config của Confluent Schema Registry
        p.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, props.getSchemaRegistryUrl());
        //Value serializer: biến Object thành bytes
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serdeFactory.valueSerializerClass());
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // large-scale safe defaults
        p.put(ProducerConfig.ACKS_CONFIG, props.getProducer().getAcks());
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, props.getProducer().isIdempotence());
        p.put(ProducerConfig.RETRIES_CONFIG, props.getProducer().getRetries());
        p.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, props.getProducer().getMaxInFlight());
        p.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, props.getProducer().getCompression());
        p.put(ProducerConfig.LINGER_MS_CONFIG, props.getProducer().getLingerMs());
        p.put(ProducerConfig.BATCH_SIZE_CONFIG, props.getProducer().getBatchSize());
        //sẽ tạo (và cache) KafkaProducer theo config.
        //KafkaTemplate sẽ dùng factory này để gửi message.
        return new DefaultKafkaProducerFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    //Tạo bean KafkaTemplate đây là (interface) mà các developer sẽ @Autowired vào code của họ để gọi hàm send()
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }
}
