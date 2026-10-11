package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.GetAdmissionSettlementPreviewUseCase;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.application.service.AdmissionSettlementPreviewService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.settlement-preview.enabled"}, havingValue = "true")
public class AdmissionSettlementPreviewConfiguration {
    private static final int TRANSACTION_TIMEOUT_SECONDS = 5;
    @Bean GetAdmissionSettlementPreviewUseCase admissionSettlementPreview(AdmissionLedgerReadPort ledger, PlatformTransactionManager manager) {
        var service = new AdmissionSettlementPreviewService(ledger);
        var transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(TRANSACTION_TIMEOUT_SECONDS);
        return account -> transaction.execute(status -> service.get(account));
    }
}
