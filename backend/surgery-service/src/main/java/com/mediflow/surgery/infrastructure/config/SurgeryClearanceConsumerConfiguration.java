package com.mediflow.surgery.infrastructure.config;

import com.mediflow.surgery.application.port.in.QueryPendingSurgeryClearancesUseCase;
import com.mediflow.surgery.application.port.in.ReactToSurgeryClearanceUseCase;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryInboxPort;
import com.mediflow.surgery.application.service.PendingSurgeryClearanceQueryService;
import com.mediflow.surgery.infrastructure.messaging.SurgeryClearanceDecoder;
import com.mediflow.surgery.messaging.consumer.*;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.*;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@Profile("!test")
@Conditional(SurgeryConsumersEnabledCondition.class)
@EnableRabbit
@EnableScheduling
public class SurgeryClearanceConsumerConfiguration {
    private static final String DLQ = "surgery.financial-clearance.dlq";

    @Bean Declarables surgeryClearanceTopology() {
        var exchange = new TopicExchange("mediflow.events",true,false);
        var deadLetters = new TopicExchange("mediflow.events.dlx",true,false);
        var queue = QueueBuilder.durable(SurgeryClearanceConsumer.QUEUE)
                .deadLetterExchange(deadLetters.getName()).deadLetterRoutingKey(DLQ).build();
        var dlq = QueueBuilder.durable(DLQ).build();
        return new Declarables(exchange,deadLetters,queue,dlq,
                BindingBuilder.bind(queue).to(exchange).with("financial.clearance.granted"),
                BindingBuilder.bind(dlq).to(deadLetters).with(DLQ));
    }

    @Bean SimpleRabbitListenerContainerFactory surgeryClearanceListenerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connection) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory,connection);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(2);
        factory.setPrefetchCount(10);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .retryPolicy(new SimpleRetryPolicy(3,java.util.Map.of(AmqpRejectAndDontRequeueException.class,false),true,true))
                .backOffOptions(100,2,500)
                .recoverer((message,cause) -> { throw new AmqpRejectAndDontRequeueException("Surgery clearance retries exhausted"); })
                .build());
        return factory;
    }

    @Bean SurgeryClearanceConsumer surgeryClearanceConsumer(SurgeryClearanceDecoder decoder,
            ReactToSurgeryClearanceUseCase clearance, SurgeryClockPort clock) {
        return new SurgeryClearanceConsumer(decoder,clearance,clock);
    }
    @Bean QueryPendingSurgeryClearancesUseCase pendingSurgeryClearances(SurgeryInboxPort inbox, SurgeryClockPort clock) {
        return new PendingSurgeryClearanceQueryService(inbox,clock);
    }
    @Bean PendingSurgeryClearanceWorker pendingSurgeryClearanceWorker(QueryPendingSurgeryClearancesUseCase pending,
            ReactToSurgeryClearanceUseCase clearance, SurgeryClearanceDecoder decoder,
            com.mediflow.surgery.application.port.in.RecordSurgeryClearanceRetryUseCase retry) {
        return new PendingSurgeryClearanceWorker(pending,clearance,decoder,retry);
    }
    @Bean com.mediflow.surgery.application.port.in.RecordSurgeryClearanceRetryUseCase surgeryClearanceRetry(
            SurgeryInboxPort inbox, SurgeryClockPort clock) {
        return new com.mediflow.surgery.application.service.SurgeryClearanceRetryService(inbox,clock);
    }
}
