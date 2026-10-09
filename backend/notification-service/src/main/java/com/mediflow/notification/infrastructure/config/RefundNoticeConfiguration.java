package com.mediflow.notification.infrastructure.config;

import java.util.Map;
import com.mediflow.notification.application.port.in.ReactToRefundNoticeUseCase;
import com.mediflow.notification.application.port.out.*;
import com.mediflow.notification.application.service.RefundNoticeService;
import com.mediflow.notification.messaging.consumer.RefundNoticeConsumer;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableRabbit
@ConditionalOnProperty(name = {"mediflow.notification.care-v1.enabled", "mediflow.notification.refund-consumer.enabled"}, havingValue = "true")
public class RefundNoticeConfiguration {
    @Bean Declarables refundNoticeTopology() {
        var events = new TopicExchange("mediflow.events", true, false); var dead = new TopicExchange("mediflow.events.dlx", true, false);
        var queue = QueueBuilder.durable("notification.refunds-v1.q").deadLetterExchange(dead.getName()).deadLetterRoutingKey("notification.refunds-v1.dlq").build();
        var dlq = QueueBuilder.durable("notification.refunds-v1.dlq").build();
        return new Declarables(events, dead, queue, dlq, BindingBuilder.bind(queue).to(events).with("payment.refunded"), BindingBuilder.bind(dlq).to(dead).with(dlq.getName()));
    }
    @Bean ReactToRefundNoticeUseCase refundNotices(CareNotificationRepositoryPort history, NotificationSourcePort sources, PlatformTransactionManager manager) {
        var service = new RefundNoticeService(history, sources); var tx = new TransactionTemplate(manager); tx.setTimeout(5);
        return command -> tx.executeWithoutResult(status -> service.receive(command));
    }
    @Bean RefundNoticeConsumer refundNoticeConsumer(RefundNoticeWirePort wire, ReactToRefundNoticeUseCase notices) {
        return new RefundNoticeConsumer(wire, notices);
    }
    @Bean SimpleRabbitListenerContainerFactory refundNoticeListenerFactory(ConnectionFactory connection,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        var factory = new SimpleRabbitListenerContainerFactory(); factory.setConnectionFactory(connection); factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setAutoStartup(autoStartup); factory.setDefaultRequeueRejected(false); factory.setConcurrentConsumers(1); factory.setMaxConcurrentConsumers(2); factory.setPrefetchCount(10);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(3, Map.of(AmqpRejectAndDontRequeueException.class, false), true, true))
                .backOffOptions(100, 2, 500).recoverer((message, error) -> { throw new AmqpRejectAndDontRequeueException("Completed-refund notice retries exhausted"); }).build());
        return factory;
    }
}
