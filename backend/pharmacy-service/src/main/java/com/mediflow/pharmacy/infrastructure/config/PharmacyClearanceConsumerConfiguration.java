package com.mediflow.pharmacy.infrastructure.config;

import com.mediflow.pharmacy.application.port.in.ProjectPrescriptionClearanceUseCase;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearanceWirePort;
import com.mediflow.pharmacy.messaging.consumer.PrescriptionClearanceConsumer;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableRabbit
@Conditional(PharmacyClearanceEnabledCondition.class)
public class PharmacyClearanceConsumerConfiguration {
    @Bean Declarables pharmacyClearanceTopology() {
        var events = new TopicExchange("mediflow.events", true, false);
        var dead = new TopicExchange("mediflow.events.dlx", true, false);
        var queue = QueueBuilder.durable("pharmacy.financial-clearance.q")
                .deadLetterExchange(dead.getName()).deadLetterRoutingKey("pharmacy.financial-clearance.dlq").build();
        var dlq = QueueBuilder.durable("pharmacy.financial-clearance.dlq").build();
        return new Declarables(events, dead, queue, dlq,
                BindingBuilder.bind(queue).to(events).with("financial.clearance.granted"),
                BindingBuilder.bind(dlq).to(dead).with(dlq.getName()));
    }

    @Bean PrescriptionClearanceConsumer prescriptionClearanceConsumer(PrescriptionClearanceWirePort decoder,
            ProjectPrescriptionClearanceUseCase project) {
        return new PrescriptionClearanceConsumer(decoder, project);
    }

    @Bean SimpleRabbitListenerContainerFactory pharmacyClearanceListenerFactory(ConnectionFactory connection,
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
                    throw new AmqpRejectAndDontRequeueException("Prescription clearance retries exhausted");
                }).build());
        return factory;
    }
}
