package it;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

public class AppInstance implements AutoCloseable {
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

                "--lsf.kafka.consumer.group-id=it-group",
                "--lsf.kafka.consumer.retry.attempts=2",
                "--lsf.kafka.consumer.retry.backoff=500ms",
                "--lsf.kafka.dlq.enabled=true",
                "--lsf.kafka.dlq.suffix=.DLQ",

                "--lsf.eventing.consume-topics[0]=demo-topic",
                "--lsf.eventing.ignore-unknown-event-type=false",
                "--lsf.eventing.idempotency.enabled=true",
                "--lsf.eventing.idempotency.store=" + store,

                "--spring.data.redis.host=" + AbstractIntegrationTest.redisHost(),
                "--spring.data.redis.port=" + AbstractIntegrationTest.redisPort(),

                "--management.endpoints.web.exposure.include=health,metrics"
        };

        this.ctx = new SpringApplicationBuilder(mainAppClass, TestProbeConfig.class)
                .run(args);

        this.port = Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));
    }




    public int port() { return port; }

    public <T> T getBean(Class<T> type) {
        return ctx.getBean(type);
    }

    @Override
    public void close() {
        ctx.close();
    }
}