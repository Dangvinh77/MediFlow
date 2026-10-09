package com.mediflow.pharmacy.infrastructure.config;

import com.mediflow.pharmacy.application.port.in.ProjectAdmissionLifecycleUseCase;
import com.mediflow.pharmacy.application.port.out.AdmissionLifecycleWirePort;
import com.mediflow.pharmacy.messaging.consumer.AdmissionLifecycleConsumer;
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

/** Admission projection intake is not public V1 medication authorization or stock activation. */
@Configuration(proxyBeanMethods = false)
@EnableRabbit
@ConditionalOnProperty(name = {"mediflow.features.care-finance-v2",
        "mediflow.pharmacy.admission-consumer.enabled"}, havingValue = "true")
public class PharmacyAdmissionConsumerConfiguration {
    @Bean
    Declarables pharmacyAdmissionTopology() {
        var events = new TopicExchange("mediflow.events", true, false);
        var dead = new TopicExchange("mediflow.events.dlx", true, false);
        var queue = QueueBuilder.durable("pharmacy.admission-lifecycle.q")
                .deadLetterExchange(dead.getName()).deadLetterRoutingKey("pharmacy.admission-lifecycle.dlq").build();
        var dlq = QueueBuilder.durable("pharmacy.admission-lifecycle.dlq").build();
        return new Declarables(events, dead, queue, dlq,
                BindingBuilder.bind(queue).to(events).with("admission.started"),
                BindingBuilder.bind(queue).to(events).with("discharge.medically.approved"),
                BindingBuilder.bind(queue).to(events).with("admission.closed"),
                BindingBuilder.bind(dlq).to(dead).with(dlq.getName()));
    }

    @Bean
    AdmissionLifecycleConsumer admissionLifecycleConsumer(AdmissionLifecycleWirePort decoder,
            ProjectAdmissionLifecycleUseCase projection) {
        return new AdmissionLifecycleConsumer(decoder, projection);
    }

    @Bean
    SimpleRabbitListenerContainerFactory pharmacyAdmissionListenerFactory(ConnectionFactory connection,
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
                    throw new AmqpRejectAndDontRequeueException("Admission lifecycle retries exhausted");
                }).build());
        return factory;
    }
}
