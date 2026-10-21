package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.GetOutpatientAdjustmentUseCase;
import com.mediflow.billing.application.port.in.ProcessOutpatientCancellationUseCase;
import com.mediflow.billing.application.port.out.OutpatientCancellationRepositoryPort;
import com.mediflow.billing.application.port.out.OutpatientCancellationWirePort;
import com.mediflow.billing.application.service.OutpatientCancellationService;
import com.mediflow.billing.messaging.consumer.OutpatientCancellationConsumer;
import java.time.Clock;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Optional V1 cancellation intake for the Billing-owned outpatient source issuers. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mediflow.billing.ledger.enabled", havingValue = "true")
public class OutpatientCancellationConfiguration {
    @Bean @ConditionalOnProperty(name = "mediflow.billing.lab-test-charge-consumer.enabled", havingValue = "true")
    Binding labRequestCancelledBinding(Queue billingQueue, TopicExchange mediflowEventsExchange) {
        return BindingBuilder.bind(billingQueue).to(mediflowEventsExchange).with(RabbitConfig.RK_LAB_REQUEST_CANCELLED);
    }
    @Bean @ConditionalOnProperty(name = "mediflow.billing.exam-charge-consumer.enabled", havingValue = "true")
    Binding appointmentExamCancelledBinding(Queue billingQueue, TopicExchange mediflowEventsExchange) {
        return BindingBuilder.bind(billingQueue).to(mediflowEventsExchange).with(RabbitConfig.RK_APPOINTMENT_EXAM_CANCELLED);
    }
    @Bean ProcessOutpatientCancellationUseCase outpatientCancellations(
            OutpatientCancellationRepositoryPort repository, PlatformTransactionManager manager) {
        var service = new OutpatientCancellationService(repository, Clock.systemUTC());
        var transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(5);
        return command -> transaction.executeWithoutResult(status -> service.receive(command));
    }
    @Bean OutpatientCancellationConsumer outpatientCancellationConsumer(OutpatientCancellationWirePort wire,
            ProcessOutpatientCancellationUseCase cancellations) {
        return new OutpatientCancellationConsumer(wire, cancellations);
    }
    @Bean GetOutpatientAdjustmentUseCase outpatientAdjustments(OutpatientCancellationRepositoryPort repository,
            PlatformTransactionManager manager) {
        var transaction = new TransactionTemplate(manager); transaction.setReadOnly(true); transaction.setTimeout(5);
        return (sourceType, sourceId) -> transaction.execute(status -> repository.get(sourceType, sourceId));
    }
}
