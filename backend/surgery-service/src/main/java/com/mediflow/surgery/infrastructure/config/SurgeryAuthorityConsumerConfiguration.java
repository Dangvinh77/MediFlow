package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.ApplySurgeryAuthorityInvalidationUseCase;
import com.mediflow.surgery.application.port.in.QuerySurgeryAuthorityInvalidationsUseCase;
import com.mediflow.surgery.application.port.in.ReceiveSurgeryAuthorityChangeUseCase;
import com.mediflow.surgery.application.port.in.RecordSurgeryAuthorityInvalidationRetryUseCase;
import com.mediflow.surgery.application.port.out.SurgeryAuthorityChangeWirePort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.messaging.consumer.SurgeryAuthorityChangeConsumer;
import java.util.Map;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
@ConditionalOnProperty(prefix = "mediflow", name = {"features.surgery.enabled", "surgery.messaging.consumers.enabled",
        "surgery.messaging.organization-authority.enabled"}, havingValue = "true")
@EnableRabbit
@EnableScheduling
public class SurgeryAuthorityConsumerConfiguration {
    public static final String DLQ = "surgery.organization-authority.dlq";
    @Bean Declarables surgeryAuthorityTopology() {
        var exchange = new TopicExchange("mediflow.events", true, false);
        var deadLetters = new TopicExchange("mediflow.events.dlx", true, false);
        var queue = QueueBuilder.durable(SurgeryAuthorityChangeConsumer.QUEUE).deadLetterExchange(deadLetters.getName()).deadLetterRoutingKey(DLQ).build();
        var dlq = QueueBuilder.durable(DLQ).build();
        return new Declarables(exchange, deadLetters, queue, dlq,
                BindingBuilder.bind(queue).to(exchange).with(ReceiveSurgeryAuthorityChangeUseCase.EVENT_TYPE),
                BindingBuilder.bind(dlq).to(deadLetters).with(DLQ));
    }
    @Bean SimpleRabbitListenerContainerFactory surgeryAuthorityListenerFactory(SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connection) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connection);
        factory.setConcurrentConsumers(1); factory.setMaxConcurrentConsumers(2); factory.setPrefetchCount(10);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new SimpleRetryPolicy(3, Map.of(AmqpRejectAndDontRequeueException.class, false), true, true))
                .backOffOptions(100, 2, 500)
                .recoverer((message, cause) -> { throw new AmqpRejectAndDontRequeueException("Surgery authority retries exhausted"); }).build());
        return factory;
    }
    @Bean SurgeryAuthorityChangeConsumer surgeryAuthorityChangeConsumer(SurgeryAuthorityChangeWirePort decoder, ReceiveSurgeryAuthorityChangeUseCase receive, SurgeryClockPort clock) {
        return new SurgeryAuthorityChangeConsumer(decoder, receive, clock);
    }
    @Bean SurgeryAuthorityInvalidationWorker surgeryAuthorityInvalidationWorker(QuerySurgeryAuthorityInvalidationsUseCase query,
            ApplySurgeryAuthorityInvalidationUseCase apply, RecordSurgeryAuthorityInvalidationRetryUseCase retry,
            @Value("${mediflow.surgery.messaging.organization-authority.batch-size:20}") int batchSize) {
        return new SurgeryAuthorityInvalidationWorker(query, apply, retry, batchSize);
    }
}
