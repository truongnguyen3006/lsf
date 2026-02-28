package it;

import org.apache.kafka.clients.admin.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.common.TopicPartition;
import java.util.Set;

public abstract class AbstractIntegrationTest {

    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.1")
    );

    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    ).withExposedPorts(6379);

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0.32"))
            .withDatabaseName("lsf_test_db")
            .withUsername("test")
            .withPassword("test");

    // Khối lệnh chạy 1 lần duy nhất khi nạp Class
    static {
        KAFKA.start();
        REDIS.start();
        MYSQL.start();

        // ÉP BUỘC Spring Boot phải dùng các URL này bằng System Properties
        // (Cách này không bao giờ bị Spring phớt lờ)
        System.setProperty("spring.datasource.url", MYSQL.getJdbcUrl());
        System.setProperty("spring.datasource.username", MYSQL.getUsername());
        System.setProperty("spring.datasource.password", MYSQL.getPassword());

        System.setProperty("spring.flyway.url", MYSQL.getJdbcUrl());
        System.setProperty("spring.flyway.user", MYSQL.getUsername());
        System.setProperty("spring.flyway.password", MYSQL.getPassword());

        try {
            createTopics();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Kafka topics", e);
        }
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

    static String kafkaBootstrap() {
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

    static void awaitGroupReady(String groupId, String topic, int expectedMembers, int expectedPartitions, Duration timeout) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap());

        long deadline = System.nanoTime() + timeout.toNanos();
        Exception last = null;

        try (AdminClient admin = AdminClient.create(props)) {
            while (System.nanoTime() < deadline) {
                try {
                    ConsumerGroupDescription desc = admin.describeConsumerGroups(List.of(groupId))
                            .describedGroups()
                            .get(groupId)
                            .get(5, TimeUnit.SECONDS);

                    int members = desc.members().size();
                    Set<TopicPartition> assigned = desc.members().stream()
                            .flatMap(m -> m.assignment().topicPartitions().stream())
                            .filter(tp -> tp.topic().equals(topic))
                            .collect(java.util.stream.Collectors.toSet());

                    if (members >= expectedMembers && assigned.size() >= expectedPartitions) {
                        return;
                    }
                } catch (Exception e) {
                    last = e;
                }

                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }

        throw new IllegalStateException(
                "Timeout waiting consumer group ready: groupId=" + groupId
                        + ", topic=" + topic
                        + ", expectedMembers=" + expectedMembers
                        + ", expectedPartitions=" + expectedPartitions,
                last
        );
    }
}