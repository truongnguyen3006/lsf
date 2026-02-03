package it;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.errors.TopicExistsException;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;


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

//    @Bean
//    public HandlerProbeAspect handlerProbeAspect(TestHandlerProbe probe) {
//        return new HandlerProbeAspect(probe);
//    }
//
//    @Aspect
//    public static class HandlerProbeAspect {
//        private final TestHandlerProbe probe;
//        public HandlerProbeAspect(TestHandlerProbe probe) { this.probe = probe; }
//
//        // pointcut vào @LsfEventHandler
//        @AfterReturning("@annotation(com.myorg.lsf.eventing.LsfEventHandler)")
//        public void afterHandled(JoinPoint jp) {
//            // handler của bạn thường có arg EventEnvelope, scan args cho chắc
//            for (Object a : jp.getArgs()) {
//                if (a instanceof EventEnvelope) {
//                    probe.markHandled();
//                    return;
//                }
//            }
//            // fallback: vẫn mark để test không bị treo nếu signature khác
//            probe.markHandled();
//        }
//    }

    @Bean
    public ApplicationRunner itEnsureTopicsOnSameCluster(org.springframework.core.env.Environment env) {
        return args -> {
            String bootstrap = env.getProperty("lsf.kafka.bootstrap-servers");
            if (bootstrap == null || bootstrap.isBlank()) {
                bootstrap = env.getProperty("spring.kafka.bootstrap-servers");
            }
            if (bootstrap == null || bootstrap.isBlank()) {
                throw new IllegalStateException(
                        "Missing bootstrap servers. Set lsf.kafka.bootstrap-servers or spring.kafka.bootstrap-servers"
                );
            }

            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);

            try (AdminClient admin = AdminClient.create(props)) {
                NewTopic demo = new NewTopic("demo-topic", 2, (short) 1);
                NewTopic dlq  = new NewTopic("demo-topic.DLQ", 2, (short) 1);
                try {
                    admin.createTopics(List.of(demo, dlq)).all().get(10, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    if (!(e.getCause() instanceof TopicExistsException)) throw e;
                }
            }
        };
    }


}
