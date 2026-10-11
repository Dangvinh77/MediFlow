package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.IssueAdmissionDepositRequestUseCase;
import com.mediflow.billing.application.port.out.AdmissionDepositRequestRepositoryPort;
import com.mediflow.billing.application.port.out.AdmissionDepositRequestWirePort;
import com.mediflow.billing.application.service.AdmissionDepositRequestService;
import com.mediflow.billing.messaging.consumer.AdmissionDepositRequestConsumer;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in ADMISSION_DEPOSIT initial request issuance. Both flags default false; disabled leaves
 * {@code admission.deposit.requested} unbound on {@value RabbitConfig#QUEUE}, same as before this
 * slice existed. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.admission-deposit-consumer.enabled"}, havingValue = "true")
public class AdmissionDepositRequestConfiguration {
    @Bean Binding admissionDepositRequestedBinding(Queue billingQueue, TopicExchange mediflowEventsExchange) {
        return BindingBuilder.bind(billingQueue).to(mediflowEventsExchange).with(RabbitConfig.RK_ADMISSION_DEPOSIT_REQUESTED);
    }
    @Bean IssueAdmissionDepositRequestUseCase issueAdmissionDepositRequests(AdmissionDepositRequestRepositoryPort repository,
            PlatformTransactionManager manager) {
        var service = new AdmissionDepositRequestService(repository);
        var transaction = new TransactionTemplate(manager); transaction.setTimeout(5);
        return command -> transaction.execute(status -> service.issue(command));
    }
    @Bean AdmissionDepositRequestConsumer admissionDepositRequestConsumer(AdmissionDepositRequestWirePort wire,
            IssueAdmissionDepositRequestUseCase requests) {
        return new AdmissionDepositRequestConsumer(wire, requests);
    }
}
