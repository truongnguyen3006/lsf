package it;

import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Testcontainers
public abstract class AbstractIntegrationTest {

    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.1")
    );

    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    @BeforeAll
    static void startContainers() throws Exception {
        KAFKA.start();
        REDIS.start();
        createTopics();
    }

    static void createTopics() throws Exception {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap());

        try (AdminClient admin = AdminClient.create(props)) {
            NewTopic demo = new NewTopic("demo-topic", 2, (short) 1);
            NewTopic dlq  = new NewTopic("demo-topic.DLQ", 2, (short) 1);
            admin.createTopics(List.of(demo, dlq)).all().get(20, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
    }

    @AfterAll
    static void stopContainers() {
        REDIS.stop();
        KAFKA.stop();
    }



    static String kafkaBootstrap() {
        // KafkaContainer may return "PLAINTEXT://localhost:xxxxx"
        return KAFKA.getBootstrapServers()
                .replace("PLAINTEXT://", "")
                .replace("SASL_PLAINTEXT://", "")
                .replace("SASL_SSL://", "")
                .replace("SSL://", "");
    }


    static String redisHost() {
        return REDIS.getHost();
    }

    static int redisPort() {
        return REDIS.getMappedPort(6379);
    }
}
