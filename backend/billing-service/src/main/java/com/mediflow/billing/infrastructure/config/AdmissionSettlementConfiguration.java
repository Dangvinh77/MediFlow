package com.mediflow.billing.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.ReactToDischargeApprovalUseCase;
import com.mediflow.billing.application.port.in.SettleAdmissionUseCase;
import com.mediflow.billing.application.port.out.AdmissionSettlementRepositoryPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.application.service.AdmissionSettlementService;
import com.mediflow.billing.messaging.consumer.DischargeApprovalConsumer;
import java.time.Clock;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Opt-in medical-discharge charge freeze + admission settlement. Both flags default false;
 * disabled leaves {@code discharge.medically.approved} unbound on {@value RabbitConfig#QUEUE} and
 * the settlements REST endpoint absent, unchanged from before this slice existed. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.settlement.enabled"}, havingValue = "true")
public class AdmissionSettlementConfiguration {
    @Bean Binding dischargeMedicallyApprovedBinding(Queue billingQueue, TopicExchange mediflowEventsExchange) {
        return BindingBuilder.bind(billingQueue).to(mediflowEventsExchange).with(RabbitConfig.RK_DISCHARGE_MEDICALLY_APPROVED);
    }
    // Two beans, each its own AdmissionSettlementService instance (stateless besides a Clock):
    // exposing one shared instance by its concrete type would also satisfy the OTHER port
    // interface it implements, giving Spring two candidates for that port and an ambiguous wire.
    @Bean ReactToDischargeApprovalUseCase reactToDischargeApproval(ProcessedEventPort processedEvent,
            AdmissionSettlementRepositoryPort repository, LedgerEventPort events, PlatformTransactionManager manager) {
        var service = new AdmissionSettlementService(processedEvent, repository, events, Clock.systemUTC());
        var transaction = new TransactionTemplate(manager); transaction.setTimeout(5);
        return event -> transaction.executeWithoutResult(status -> service.onDischargeMedicallyApproved(event));
    }
    @Bean SettleAdmissionUseCase settleAdmissionUseCase(ProcessedEventPort processedEvent,
            AdmissionSettlementRepositoryPort repository, LedgerEventPort events, PlatformTransactionManager manager) {
        var service = new AdmissionSettlementService(processedEvent, repository, events, Clock.systemUTC());
        var transaction = new TransactionTemplate(manager); transaction.setTimeout(5);
        return (accountId, request, actor, correlation) -> transaction.execute(status -> service.settle(accountId, request, actor, correlation));
    }
    @Bean DischargeApprovalConsumer dischargeApprovalConsumer(ObjectMapper mapper, ReactToDischargeApprovalUseCase discharges) {
        return new DischargeApprovalConsumer(mapper, discharges);
    }
}
