package com.myorg.lsf.kafka;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import lombok.Data;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

import java.util.HashMap;
import java.util.Map;

@Data
public class SerdeFactory {
    private final String SchemaRegistryUrl;
    //Common props for all Confluent schema-aware components
    public Map<String, Object> schemaRegistryProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SchemaRegistryUrl);
        return props;
    }
    //For Spring Kafka ProducerFactory
    public Class<?> valueSerializerClass(){
        return KafkaJsonSchemaSerializer.class;
    }
    //For Spring Kafka ConsumerFactory
    public Class<?> valueDeserializerClass(){
        return KafkaJsonSchemaDeserializer.class;
    }
    // Convenience
    public Serde<String> stringKeySerde() {
        return Serdes.String();
    }
    //  For Kafka Streams
    //Java generics bị erase ở runtime
    // nên compiler không thể chứng minh cast này an toàn → warning “unchecked cast”.
    //Annotation này chỉ để tắt warning cho đoạn cast đó
    @SuppressWarnings("unchecked")
    public <T> Serde<T> jsonSchemaSerdeForStreams(Class<T> clazz) {
        try {
            // optional dependency
            // io.confluent.kafka.streams.serdes.json.KafkaJsonSchemaSerde<T>
            Class<?> serdeClass = Class.forName("io.confluent.kafka.streams.serdes.json.KafkaJsonSchemaSerde");
            // getConstructor(Class.class) lấy constructor nhận vào Class
            // newInstance(clazz) tạo serde cho type T
            Object serdeObj = serdeClass.getConstructor(Class.class).newInstance(clazz);
            Serde<T> serde = (Serde<T>) serdeObj;
            //Cấu hình serde với Schema Registry
            //copy các config chung cho schema registry
            Map<String, Object> cfg = new HashMap<>(schemaRegistryProps());
            // config mà Confluent JSON Schema serde dùng để biết khi deserialize thì map JSON → class nào.
            cfg.put("json.value.type", clazz.getName()); // giống cái từng làm trong Inventory
            //true = serde dùng cho key
            //false = serde dùng cho value
            serde.configure(cfg, false);
            return serde;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Kafka Streams JSON Schema serde not available. Add dependency kafka-streams-json-schema-serde.", e);
        }
    }

}
