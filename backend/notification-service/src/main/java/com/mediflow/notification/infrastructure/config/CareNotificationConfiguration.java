package com.mediflow.notification.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.mediflow.notification.application.port.in.ReactToCarePaymentReceiptUseCase;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.service.CarePaymentReceiptService;

@Configuration(proxyBeanMethods = false)
public class CareNotificationConfiguration {
    @Bean ReactToCarePaymentReceiptUseCase carePaymentReceipts(CareNotificationRepositoryPort repository,
            PlatformTransactionManager manager) {
        var service = new CarePaymentReceiptService(repository);
        var transactions = new TransactionTemplate(manager);
        return command -> transactions.executeWithoutResult(status -> service.receive(command));
    }
}
