package com.mediflow.billing.infrastructure.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.port.in.ProcessLedgerPaymentUseCase;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.LedgerPaymentRepositoryPort;
import com.mediflow.billing.application.service.LedgerPaymentService;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mediflow.billing.ledger.enabled", havingValue = "true")
public class LedgerPaymentConfiguration {
    @Bean
    ProcessLedgerPaymentUseCase processLedgerPaymentUseCase(LedgerPaymentRepositoryPort repository,
            LedgerEventPort events, LedgerPaymentMapper mapper, PlatformTransactionManager manager) {
        var service = new LedgerPaymentService(repository, events, mapper, Clock.systemUTC());
        var transactions = new TransactionTemplate(manager);
        return (request, command, actor, correlation) -> transactions.execute(status ->
                service.complete(request, command, actor, correlation));
    }
}
