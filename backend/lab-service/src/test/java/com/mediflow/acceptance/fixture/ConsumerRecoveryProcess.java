package com.mediflow.acceptance.fixture;

import com.mediflow.lab.LabServiceApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Test-only launcher: retain the production retry advice, pause after its committed effect, before ACK. */
public final class ConsumerRecoveryProcess {
    private ConsumerRecoveryProcess() {}

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(LabServiceApplication.class, Barrier.class);
        application.run(args);
    }

    @Configuration(proxyBeanMethods = false)
    public static class Barrier {
        @Bean
        static BeanPostProcessor afterCommitBeforeAckBarrier() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!"labListenerRetryInterceptor".equals(name)) return bean;
                    MethodInterceptor productionRetry = (MethodInterceptor) bean;
                    return (MethodInterceptor) invocation -> {
                        Object result = productionRetry.invoke(invocation);
                        Message delivery = null;
                        for (Object argument : invocation.getArguments()) {
                            if (argument instanceof Message message) delivery = message;
                        }
                        if (delivery == null) throw new IllegalStateException("Expected single-message delivery");
                        String routeFile = System.getenv("RECOVERY_BARRIER_ROUTE_FILE");
                        if (routeFile != null && (!Files.exists(Path.of(routeFile))
                                || !Files.readString(Path.of(routeFile)).equals(delivery.getMessageProperties().getReceivedRoutingKey()))) {
                            return result;
                        }
                        Path marker = Path.of(System.getenv("RECOVERY_DELIVERY_MARKER"));
                        Files.write(marker, delivery.getBody());
                        Files.writeString(Path.of(marker + ".redelivered"),
                                String.valueOf(delivery.getMessageProperties().isRedelivered()));
                        String holdFile = System.getenv("RECOVERY_HOLD_ACK_FILE");
                        if (holdFile != null ? Files.exists(Path.of(holdFile))
                                : Boolean.parseBoolean(System.getenv("RECOVERY_HOLD_ACK"))) {
                            new CountDownLatch(1).await();
                        }
                        return result;
                    };
                }
            };
        }
    }
}
