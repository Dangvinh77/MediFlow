package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.ProcessLabQuotedPaymentUseCase;
import com.mediflow.billing.application.service.LedgerPaymentService;
import com.mediflow.billing.application.service.ProcessLabQuotedPaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.outpatient-quotes.enabled"}, havingValue = "true")
public class LabQuotedPaymentConfiguration {
    @Bean ProcessLabQuotedPaymentUseCase processLabQuotedPaymentUseCase(LedgerPaymentService ledger,
            PlatformTransactionManager manager) {
        var service = new ProcessLabQuotedPaymentService(ledger);
        var transaction = new TransactionTemplate(manager);
        transaction.setTimeout(5);
        return (requestId, command, idempotencyKey, actorId, correlation) ->
                transaction.execute(status -> service.complete(requestId, command, idempotencyKey, actorId, correlation));
    }
}
