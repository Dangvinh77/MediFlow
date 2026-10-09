package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.IssueSurgeryChargeUseCase;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.SurgeryPlannedRequestRepositoryPort;
import com.mediflow.billing.application.port.out.SurgeryChargeWirePort;
import com.mediflow.billing.application.service.SurgeryPlannedRequestService;
import com.mediflow.billing.messaging.consumer.SurgeryChargeConsumer;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.surgery-charge-consumer.enabled"}, havingValue = "true")
public class SurgeryChargeConfiguration {
    @Bean IssueSurgeryChargeUseCase issueSurgeryCharges(SurgeryPlannedRequestRepositoryPort repository, PriceCatalogPort prices,
            LedgerEventPort events, PlatformTransactionManager manager,
            com.mediflow.billing.application.port.out.SurgeryCancellationRepositoryPort cancellations) {
        var service = new SurgeryPlannedRequestService(repository, prices, events, Clock.systemUTC(), cancellations);
        var transaction = new TransactionTemplate(manager); transaction.setTimeout(5);
        return command -> transaction.execute(status -> service.issue(command));
    }
    @Bean SurgeryChargeConsumer surgeryChargeConsumer(SurgeryChargeWirePort wire, IssueSurgeryChargeUseCase charges,
            org.springframework.beans.factory.ObjectProvider<com.mediflow.billing.application.port.in.ProcessSurgeryCancellationUseCase> cancellations) {
        return new SurgeryChargeConsumer(wire, charges, cancellations.getIfAvailable());
    }
}
