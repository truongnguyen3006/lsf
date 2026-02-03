package it;

import com.myorg.lsf.contracts.core.envelope.EventEnvelope;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.apache.kafka.common.errors.TopicExistsException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.AbstractMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.apache.kafka.common.TopicPartition;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;


import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Start a real Spring Boot app on a random port for IT.
 * We attach a small test-only configuration (probe + aspect) so tests can assert handler invocations.
 */
public class AppInstance implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AppInstance.class);

    private final ConfigurableApplicationContext ctx;
    private final int port;

    public AppInstance(Class<?> mainAppClass, String appName, String store) {
        String bs = AbstractIntegrationTest.kafkaBootstrap();

        String[] args = new String[] {
                "--server.port=0",
                "--spring.application.name=" + appName,

                // IMPORTANT: override cứng, không cho app.yml đè
                "--lsf.kafka.bootstrap-servers=" + bs,
                "--spring.kafka.bootstrap-servers=" + bs,

                "--lsf.kafka.schema-registry-url=mock://lsf-it",
                "--spring.kafka.properties.schema.registry.url=mock://lsf-it",

                "--spring.kafka.producer.value-serializer=io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer",
                "--spring.kafka.consumer.value-deserializer=io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer",
                "--spring.kafka.consumer.properties.json.value.type=com.myorg.lsf.contracts.core.envelope.EventEnvelope",
                "--spring.kafka.consumer.auto-offset-reset=earliest",


                // avoid long blocking when Kafka metadata is not ready
                "--spring.kafka.producer.properties.max.block.ms=5000",
                "--spring.kafka.producer.properties.request.timeout.ms=5000",
                "--spring.kafka.producer.properties.delivery.timeout.ms=8000",

                "--lsf.kafka.consumer.group-id=it-group",
                "--lsf.kafka.consumer.retry.attempts=2",
                "--lsf.kafka.consumer.retry.backoff=500ms",
                "--lsf.kafka.dlq.enabled=true",
                "--lsf.kafka.dlq.suffix=.DLQ",

                "--lsf.eventing.consume-topics=demo-topic",

                "--lsf.eventing.ignore-unknown-event-type=false",
                "--lsf.eventing.idempotency.enabled=true",
                "--lsf.eventing.idempotency.store=" + store,

                "--spring.kafka.consumer.auto-offset-reset=earliest",
                "--spring.kafka.consumer.properties.auto.offset.reset=earliest",
                "--lsf.kafka.consumer.batch=false",
                "--lsf.kafka.consumer.concurrency=1",

                "--spring.data.redis.host=" + AbstractIntegrationTest.redisHost(),
                "--spring.data.redis.port=" + AbstractIntegrationTest.redisPort(),

                "--management.endpoints.web.exposure.include=health,metrics",
                "--spring.aop.auto=false",


        };

        this.ctx = new SpringApplicationBuilder(mainAppClass, ItTestConfig.class)
                .run(args);

        this.port = Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));

        log.info("IT app started: name={} port={} store={}", appName, port, store);
    }

    public int port() { return port; }

    public <T> T getBean(Class<T> type) {
        return ctx.getBean(type);
    }

    @Override
    public void close() {
        ctx.close();
    }

    @Configuration
    public static class ItTestConfig {

        private static final Logger log = LoggerFactory.getLogger(ItTestConfig.class);

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

            public HandlerProbeAspect(TestHandlerProbe probe) {
                this.probe = probe;
            }

            @AfterReturning("@annotation(com.myorg.lsf.eventing.LsfEventHandler)")
            public void afterHandled(JoinPoint jp) {
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

        /**
         * Ensure topics exist on the same broker the app uses.
         * This also helps to avoid rare races where metadata isn't ready yet.
         */
        @Bean
        public ApplicationRunner itEnsureTopicsOnSameCluster(Environment env) {
            return args -> {
                String bootstrap = env.getProperty("lsf.kafka.bootstrap-servers");
                if (bootstrap == null || bootstrap.isBlank()) {
                    bootstrap = env.getProperty("spring.kafka.bootstrap-servers");
                }
                if (bootstrap == null || bootstrap.isBlank()) {
                    throw new IllegalStateException("Missing bootstrap servers (lsf.kafka.bootstrap-servers / spring.kafka.bootstrap-servers)");
                }

                log.info("IT ensureTopics using bootstrap.servers={}", bootstrap);

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

                        var desc = admin.describeTopics(List.of("demo-topic", "demo-topic.DLQ"))
                                .all().get(10, TimeUnit.SECONDS);

                        int p1 = desc.get("demo-topic").partitions().size();
                        int p2 = desc.get("demo-topic.DLQ").partitions().size();
                        if (p1 >= 2 && p2 >= 2) {
                            log.info("IT topics ready: demo-topic partitions={}, dlq partitions={}", p1, p2);
                            return;
                        }

                        throw new IllegalStateException("Partitions not ready yet demo=" + p1 + " dlq=" + p2);
                    } catch (Exception e) {
                        last = e;
                        Thread.sleep(500);
                    }
                }

                throw new IllegalStateException("Failed to ensure topics on bootstrap=" + bootstrap, last);
            };
        }
    }
    public void waitForTopicAssignment(String topic, int expectedTotalPartitions) {
        KafkaListenerEndpointRegistry registry = ctx.getBean(KafkaListenerEndpointRegistry.class);

        long deadline = System.currentTimeMillis() + 30_000; // 30s
        Exception last = null;

        while (System.currentTimeMillis() < deadline) {
            try {
                for (MessageListenerContainer c : registry.getListenerContainers()) {
                    if (!(c instanceof AbstractMessageListenerContainer<?, ?> amlc)) continue;

                    ContainerProperties cp = amlc.getContainerProperties();
                    if (!matchesTopic(cp, topic)) continue;

                    int assigned = countAssignedPartitions(c);
                    if (assigned >= expectedTotalPartitions) {
                        return; // ✅ ready
                    }
                }
            } catch (Exception e) {
                last = e;
            }

            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }

        String available = registry.getListenerContainers().stream()
                .map(this::describeContainer)
                .collect(Collectors.joining("\n"));

        throw new IllegalStateException(
                "Timeout waiting assignment for topic='" + topic + "', expectedTotalPartitions=" + expectedTotalPartitions
                        + "\nAvailable listener containers:\n" + available,
                last
        );
    }

    private boolean matchesTopic(ContainerProperties cp, String topic) {
        String[] topics = cp.getTopics();
        if (topics != null && Arrays.asList(topics).contains(topic)) return true;

        var pattern = cp.getTopicPattern();
        return pattern != null && pattern.matcher(topic).matches();
    }

    @SuppressWarnings("unchecked")
    private int countAssignedPartitions(MessageListenerContainer container) {
        if (container instanceof ConcurrentMessageListenerContainer<?, ?> cc) {
            int sum = 0;
            for (KafkaMessageListenerContainer<?, ?> child : cc.getContainers()) {
                sum += sizeOfAssigned(child);
            }
            return sum;
        }

        if (container instanceof KafkaMessageListenerContainer<?, ?> km) {
            return sizeOfAssigned(km);
        }

        return 0;
    }

    private int sizeOfAssigned(Object km) {
        // Spring Kafka versions khác nhau -> cố lấy getAssignedPartitions() nếu có
        try {
            Object v = km.getClass().getMethod("getAssignedPartitions").invoke(km);
            if (v instanceof Collection<?> col) return col.size();
            if (v instanceof TopicPartition[] arr) return arr.length;
        } catch (Exception ignored) {}
        return 0;
    }

    private String describeContainer(MessageListenerContainer c) {
        String id = c.getListenerId();
        String topics = "?";
        try {
            if (c instanceof AbstractMessageListenerContainer<?, ?> amlc) {
                ContainerProperties cp = amlc.getContainerProperties();
                String[] t = cp.getTopics();
                if (t != null) topics = Arrays.toString(t);
                else if (cp.getTopicPattern() != null) topics = "pattern:" + cp.getTopicPattern();
            }
        } catch (Exception ignored) {}
        return "id=" + id + ", topics=" + topics + ", running=" + c.isRunning();
    }


}
