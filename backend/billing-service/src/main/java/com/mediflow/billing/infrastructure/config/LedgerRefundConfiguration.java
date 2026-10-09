package com.mediflow.billing.infrastructure.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.port.in.RefundLedgerPaymentUseCase;
import com.mediflow.billing.application.port.out.*;
import com.mediflow.billing.application.service.LedgerRefundService;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.refunds.enabled"}, havingValue = "true")
public class LedgerRefundConfiguration {
    @Bean
    RefundLedgerPaymentUseCase refundLedgerPaymentUseCase(LedgerPaymentRepositoryPort payments,
            LedgerRefundRepositoryPort refunds, LedgerEventPort events, LedgerPaymentMapper mapper,
            PlatformTransactionManager manager) {
        var service = new LedgerRefundService(payments, refunds, events, mapper, Clock.systemUTC());
        var transaction = new TransactionTemplate(manager);
        transaction.setTimeout(5);
        return (original, command, actor, correlation) -> transaction.execute(status -> service.refund(original, command, actor, correlation));
    }
}
