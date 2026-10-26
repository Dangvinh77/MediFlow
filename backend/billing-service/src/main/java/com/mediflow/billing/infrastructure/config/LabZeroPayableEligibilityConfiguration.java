package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.GrantLabZeroPayableEligibilityUseCase;
import com.mediflow.billing.application.port.out.LabZeroPayableEligibilityRepositoryPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import com.mediflow.billing.application.service.GrantLabZeroPayableEligibilityService;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.outpatient-quotes.enabled",
        "mediflow.billing.zero-payable-eligibility.enabled"}, havingValue = "true")
public class LabZeroPayableEligibilityConfiguration {
    @Bean GrantLabZeroPayableEligibilityUseCase grantLabZeroPayableEligibility(
            LedgerPaymentRepositoryPort ledger, LabZeroPayableEligibilityRepositoryPort receipts,
            OutpatientQuoteRepositoryPort quotes, LedgerEventPort events, PlatformTransactionManager manager) {
        var service = new GrantLabZeroPayableEligibilityService(ledger, receipts, quotes, events, Clock.systemUTC());
        var transaction = new TransactionTemplate(manager);
        transaction.setTimeout(5);
        return (requestId, quoteId, actorId, key, correlation) ->
                transaction.execute(status -> service.grant(requestId, quoteId, actorId, key, correlation));
    }
}
