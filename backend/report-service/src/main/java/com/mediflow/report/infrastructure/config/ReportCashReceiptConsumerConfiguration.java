package com.mediflow.report.infrastructure.config;

import com.mediflow.report.application.port.in.ApplyCashReceiptUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import com.mediflow.report.messaging.consumer.CashReceiptConsumer;
import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Isolated classified gross receipts; not earned revenue, refunds, settlement or read publication. */
@Configuration(proxyBeanMethods = false)
@EnableRabbit
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2",
        "mediflow.report.cash-receipt-consumer.enabled"}, havingValue = "true")
public class ReportCashReceiptConsumerConfiguration {
    @Bean
    Declarables reportCashReceiptTopology() {
        var events = new TopicExchange("mediflow.events", true, false);
        var dead = new TopicExchange("mediflow.events.dlx", true, false);
        var queue = QueueBuilder.durable("report.cash-receipts-v2.q")
                .deadLetterExchange(dead.getName()).deadLetterRoutingKey("report.cash-receipts-v2.dlq").build();
        var dlq = QueueBuilder.durable("report.cash-receipts-v2.dlq").build();
        return new Declarables(events, dead, queue, dlq,
                BindingBuilder.bind(queue).to(events).with("payment.completed"),
                BindingBuilder.bind(dlq).to(dead).with(dlq.getName()));
    }

    @Bean
    CashReceiptConsumer cashReceiptConsumer(CareFinanceWirePort decoder, ApplyCashReceiptUseCase receipts) {
        return new CashReceiptConsumer(decoder, receipts);
    }

    @Bean
    SimpleRabbitListenerContainerFactory reportCashReceiptListenerFactory(ConnectionFactory connection,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connection);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setAutoStartup(autoStartup);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(2);
        factory.setPrefetchCount(10);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new org.springframework.retry.policy.SimpleRetryPolicy(3,
                        Map.of(AmqpRejectAndDontRequeueException.class, false), true, true))
                .backOffOptions(100, 2, 500)
                .recoverer((message, error) -> {
                    throw new AmqpRejectAndDontRequeueException("Cash receipt retries exhausted");
                }).build());
        return factory;
    }
}
