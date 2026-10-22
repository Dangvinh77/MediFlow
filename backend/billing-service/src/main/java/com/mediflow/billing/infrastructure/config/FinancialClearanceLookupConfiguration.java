package com.mediflow.billing.infrastructure.config;

import java.time.Clock;
import com.mediflow.billing.application.port.in.LookupFinancialClearanceUseCase;
import com.mediflow.billing.application.port.in.StartOutpatientExecutionUseCase;
import com.mediflow.billing.application.port.out.FinancialClearanceAuthorityPort;
import com.mediflow.billing.application.port.out.OutpatientExecutionStartRepositoryPort;
import com.mediflow.billing.application.service.LookupFinancialClearanceService;
import com.mediflow.billing.application.service.StartOutpatientExecutionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mediflow.billing.clearance-lookup.enabled", havingValue = "true")
public class FinancialClearanceLookupConfiguration {
    @Bean LookupFinancialClearanceUseCase lookupFinancialClearanceUseCase(FinancialClearanceAuthorityPort repository) {
        return new LookupFinancialClearanceService(repository, Clock.systemUTC());
    }

    @Bean StartOutpatientExecutionUseCase startOutpatientExecutionUseCase(FinancialClearanceAuthorityPort authority,
            OutpatientExecutionStartRepositoryPort repository, PlatformTransactionManager manager) {
        var service = new StartOutpatientExecutionService(authority, repository, Clock.systemUTC());
        var transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(5);
        return (clearanceId, command, correlationId) -> transaction.execute(
                status -> service.start(clearanceId, command, correlationId));
    }
}
