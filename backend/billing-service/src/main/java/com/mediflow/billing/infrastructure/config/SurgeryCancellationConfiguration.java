package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.ProcessSurgeryCancellationUseCase;
import com.mediflow.billing.application.port.out.SurgeryCancellationRepositoryPort;
import com.mediflow.billing.application.port.out.SurgeryCancellationWirePort;
import com.mediflow.billing.application.service.SurgeryCancellationService;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.messaging.consumer.SurgeryCancellationConsumer;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.surgery-charge-consumer.enabled"}, havingValue = "true")
public class SurgeryCancellationConfiguration {
    @Bean com.mediflow.billing.application.port.in.GetSurgeryCancellationUseCase getSurgeryCancellation(
            SurgeryCancellationRepositoryPort repository, PlatformTransactionManager manager) {
        var transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        transaction.setTimeout(5);
        return caseId -> transaction.execute(status -> repository.get(caseId));
    }
    @Bean ProcessSurgeryCancellationUseCase surgeryCancellations(SurgeryCancellationRepositoryPort repository,
            PlatformTransactionManager manager) {
        var clock = Clock.systemUTC();
        var service = new SurgeryCancellationService(repository, clock);
        var transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(5);
        return new ProcessSurgeryCancellationUseCase() {
            @Override public void receive(com.mediflow.billing.application.dto.command.SurgeryCancellationCommand c) {
                transaction.executeWithoutResult(status -> service.receive(c));
            }
            @Override public void recover(UUID caseId) {
                try {
                    transaction.executeWithoutResult(status -> service.recover(caseId));
                } catch (BillingRuleException invalid) {
                    // A bad early fact must not poison a later valid case-created delivery.
                    transaction.executeWithoutResult(status -> repository.reject(caseId, invalid.getCode()));
                }
            }
            @Override public void recoverPending() {
                var due = transaction.execute(status -> repository.dueCases(20));
                for (var caseId : due) {
                    try { recover(caseId); }
                    catch (RuntimeException transientFailure) {
                        transaction.executeWithoutResult(status -> repository.defer(caseId, clock.instant().plusSeconds(60)));
                    }
                }
            }
        };
    }
    @Bean SurgeryCancellationConsumer surgeryCancellationConsumer(SurgeryCancellationWirePort wire, ProcessSurgeryCancellationUseCase cancellations) {
        return new SurgeryCancellationConsumer(wire, cancellations);
    }
    @Bean SurgeryCancellationRecoveryWorker surgeryCancellationRecoveryWorker(ProcessSurgeryCancellationUseCase cancellations) {
        return new SurgeryCancellationRecoveryWorker(cancellations);
    }
}
