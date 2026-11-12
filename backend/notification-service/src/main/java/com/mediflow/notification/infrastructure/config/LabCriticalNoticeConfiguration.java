package com.mediflow.notification.infrastructure.config;

import java.util.Map;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.port.out.StaffCriticalNoticePort;
import com.mediflow.notification.infrastructure.messaging.LabCriticalNoticeDecoder;
import com.mediflow.notification.infrastructure.persistence.adapter.JdbcStaffCriticalNoticeAdapter;

/** Own queue for critical Lab values, off unless {@code mediflow.notification.lab-critical.enabled=true}. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mediflow.notification.lab-critical.enabled", havingValue = "true")
public class LabCriticalNoticeConfiguration {
    public static final String QUEUE = "notification.lab-critical-v1.q";

    @Bean public Declarables labCriticalNoticeTopology() {
        var events = new TopicExchange("mediflow.events", true, false);
        var dead = new TopicExchange("mediflow.events.dlx", true, false);
        var queue = QueueBuilder.durable(QUEUE).deadLetterExchange(dead.getName())
                .deadLetterRoutingKey("notification.lab-critical-v1.dlq").build();
        var dlq = QueueBuilder.durable("notification.lab-critical-v1.dlq").build();
        return new Declarables(events, dead, queue, dlq,
                BindingBuilder.bind(queue).to(events).with("lab.critical.raised"),
                BindingBuilder.bind(dlq).to(dead).with(dlq.getName()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableRabbit
    // Component scanning registers nested configurations on their own: repeat the switch here.
    @ConditionalOnProperty(name = "mediflow.notification.lab-critical.enabled", havingValue = "true")
    static class Intake {
        @Bean StaffCriticalNoticePort staffCriticalNotices(@Qualifier("notificationJdbcTemplate") JdbcTemplate jdbc) {
            return new JdbcStaffCriticalNoticeAdapter(jdbc);
        }

        @Bean LabCriticalNoticeDecoder labCriticalNoticeDecoder(ObjectMapper mapper, StaffCriticalNoticePort notices) {
            return new LabCriticalNoticeDecoder(mapper, notices);
        }

        @Bean LabCriticalNoticeListener labCriticalNoticeListener(LabCriticalNoticeDecoder decoder) {
            return new LabCriticalNoticeListener(decoder);
        }

        @Bean SimpleRabbitListenerContainerFactory labCriticalNoticeListenerFactory(ConnectionFactory connection,
                @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
            var factory = new SimpleRabbitListenerContainerFactory();
            factory.setConnectionFactory(connection);
            factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
            factory.setAutoStartup(autoStartup);
            factory.setDefaultRequeueRejected(false);
            factory.setPrefetchCount(10);
            factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                    .retryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(3,
                            Map.of(AmqpRejectAndDontRequeueException.class, false), true, true))
                    .backOffOptions(100, 2, 500)
                    .recoverer((message, error) -> {
                        throw new AmqpRejectAndDontRequeueException("Critical Lab notice retries exhausted");
                    }).build());
            return factory;
        }
    }

    public static class LabCriticalNoticeListener {
        private final LabCriticalNoticeDecoder decoder;

        LabCriticalNoticeListener(LabCriticalNoticeDecoder decoder) {
            this.decoder = decoder;
        }

        @RabbitListener(queues = QUEUE, containerFactory = "labCriticalNoticeListenerFactory")
        public void onMessage(Message message) {
            decoder.receive(message.getBody());
        }
    }
}
