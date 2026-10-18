package com.mediflow.acceptance.fixture;

import com.mediflow.clinical.ClinicalServiceApplication;
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
        SpringApplication application = new SpringApplication(ClinicalServiceApplication.class, Barrier.class);
        application.run(args);
    }

    @Configuration(proxyBeanMethods = false)
    public static class Barrier {
        @Bean
        static BeanPostProcessor afterCommitBeforeAckBarrier() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if ("clinicalClearanceListenerContainerFactory".equals(name)
                            && System.getenv("RECOVERY_BARRIER_ROUTE_FILE") != null) {
                        var factory = (org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory) bean;
                        factory.setContainerCustomizer(container -> {
                            var listener = (org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener)
                                    container.getMessageListener();
                            container.setMessageListener((org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener)
                                    (message, channel) -> {
                                        listener.onMessage(message, channel);
                                        afterCommit(message);
                                    });
                        });
                        return factory;
                    }
                    if (!"clinicalListenerRetryInterceptor".equals(name)) return bean;
                    MethodInterceptor productionRetry = (MethodInterceptor) bean;
                    return (MethodInterceptor) invocation -> {
                        Object result = productionRetry.invoke(invocation);
                        Message delivery = null;
                        for (Object argument : invocation.getArguments()) {
                            if (argument instanceof Message message) delivery = message;
                        }
                        if (delivery == null) throw new IllegalStateException("Expected single-message delivery");
                        afterCommit(delivery);
                        return result;
                    };
                }
            };
        }

        private static void afterCommit(Message delivery) throws Exception {
            String routeFile = System.getenv("RECOVERY_BARRIER_ROUTE_FILE");
            if (routeFile != null && (!Files.exists(Path.of(routeFile))
                    || !Files.readString(Path.of(routeFile)).equals(delivery.getMessageProperties().getReceivedRoutingKey()))) {
                return;
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
        }
    }
}
