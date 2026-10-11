package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.AllocateAdmissionDepositUseCase;
import com.mediflow.billing.application.port.out.AdmissionDepositAllocationStorePort;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.service.AdmissionDepositAllocationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.settlement-preview.enabled",
        "mediflow.billing.deposit-allocation.enabled"}, havingValue = "true")
public class AdmissionDepositAllocationConfiguration {
    @Bean public AllocateAdmissionDepositUseCase allocateAdmissionDeposit(AdmissionDepositAllocationStorePort store,
            AdmissionLedgerReadPort ledger, LedgerEventPort events, PlatformTransactionManager manager) {
        var service = new AdmissionDepositAllocationService(store, ledger, events);
        var transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(5);
        return command -> transaction.execute(status -> service.allocate(command));
    }
}
