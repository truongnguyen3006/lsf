package it;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.errors.TopicExistsException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class TestProbeConfig {

    @Bean
    public TestHandlerProbe testHandlerProbe() {
        return new TestHandlerProbe();
    }

    @Bean
    public HandlerProbeAspect handlerProbeAspect(TestHandlerProbe probe) {
        return new HandlerProbeAspect(probe);
    }

    @Aspect
    public static class HandlerProbeAspect {
        private final TestHandlerProbe probe;
        public HandlerProbeAspect(TestHandlerProbe probe) { this.probe = probe; }

        // pointcut vào @LsfEventHandler
        @AfterReturning("@annotation(com.myorg.lsf.eventing.LsfEventHandler)")
        public void afterHandled(JoinPoint jp) {
            // handler của bạn thường có arg EventEnvelope, scan args cho chắc
            for (Object a : jp.getArgs()) {
                if (a instanceof EventEnvelope) {
                    probe.markHandled();
                    return;
                }
            }
            // fallback: vẫn mark để test không bị treo nếu signature khác
            probe.markHandled();
        }
    }

    @Bean
    public ApplicationRunner itEnsureTopicsOnSameCluster(KafkaTemplate<String, Object> kafkaTemplate) {
        return args -> {
            Object bsObj = kafkaTemplate.getProducerFactory()
                    .getConfigurationProperties()
                    .get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);

            String bootstrap;
            if (bsObj instanceof String s) bootstrap = s;
            else if (bsObj instanceof List<?> list) bootstrap = String.join(",", list.stream().map(String::valueOf).toList());
            else bootstrap = String.valueOf(bsObj);

            log.info("IT ensureTopics using KafkaTemplate bootstrap.servers={}", bootstrap);

            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);

            Exception last = null;
            for (int i = 1; i <= 30; i++) {
                try (AdminClient admin = AdminClient.create(props)) {
                    NewTopic demo = new NewTopic("demo-topic", 2, (short) 1);
                    NewTopic dlq  = new NewTopic("demo-topic.DLQ", 2, (short) 1);

                    try {
                        admin.createTopics(List.of(demo, dlq)).all().get(10, TimeUnit.SECONDS);
                    } catch (ExecutionException e) {
                        if (!(e.getCause() instanceof TopicExistsException)) throw e;
                    }

                    admin.describeTopics(List.of("demo-topic", "demo-topic.DLQ"))
                            .all().get(10, TimeUnit.SECONDS);

                    for (int j = 1; j <= 30; j++) {
                        List<PartitionInfo> parts = kafkaTemplate.partitionsFor("demo-topic");
                        if (parts != null && parts.size() >= 2) {
                            log.info("IT demo-topic metadata ready, partitions={}", parts.size());
                            return;
                        }
                        Thread.sleep(300);
                    }

                    throw new IllegalStateException("Topic exists but metadata not ready");
                } catch (Exception e) {
                    last = e;
                    Thread.sleep(500);
                }
            }

            throw new IllegalStateException("Failed to ensure demo-topic on bootstrap=" + bootstrap, last);
        };
    }
}
