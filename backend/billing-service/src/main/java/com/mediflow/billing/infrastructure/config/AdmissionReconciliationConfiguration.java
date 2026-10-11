package com.mediflow.billing.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.ReconcileAdmissionUseCase;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.service.AdmissionReconciliationService;
import com.mediflow.billing.infrastructure.persistence.adapter.AdmissionReconciliationStoreAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.settlement-preview.enabled",
        "mediflow.billing.reconciliation.enabled"}, havingValue = "true")
public class AdmissionReconciliationConfiguration {
    @Bean ReconcileAdmissionUseCase reconcileAdmission(AdmissionLedgerReadPort ledger, LedgerEventPort events,
            JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager manager) {
        var service = new AdmissionReconciliationService(ledger, new AdmissionReconciliationStoreAdapter(jdbc, mapper), events);
        var transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(5);
        return command -> transaction.execute(status -> service.reconcile(command));
    }
}
