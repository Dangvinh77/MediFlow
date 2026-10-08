package com.mediflow.notification.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.mediflow.notification.application.dto.command.AdmissionClosedCommand;
import com.mediflow.notification.application.dto.command.AdmissionDepositRequestedCommand;
import com.mediflow.notification.application.dto.command.AdmissionStartedCommand;
import com.mediflow.notification.application.dto.command.SurgeryCancelledNoticeCommand;
import com.mediflow.notification.application.dto.command.SurgeryReadyCommand;
import com.mediflow.notification.application.port.in.ReactToCarePaymentReceiptUseCase;
import com.mediflow.notification.application.port.in.ReactToCareProjectionUseCase;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.service.CarePaymentReceiptService;
import com.mediflow.notification.application.service.CareProjectionService;

@Configuration(proxyBeanMethods = false)
public class CareNotificationConfiguration {
    @Bean ReactToCarePaymentReceiptUseCase carePaymentReceipts(CareNotificationRepositoryPort repository,
            PlatformTransactionManager manager) {
        var service = new CarePaymentReceiptService(repository);
        var transactions = new TransactionTemplate(manager);
        return command -> transactions.executeWithoutResult(status -> service.receive(command));
    }

    @Bean ReactToCareProjectionUseCase careProjections(CareNotificationRepositoryPort repository,
            PlatformTransactionManager manager) {
        var service = new CareProjectionService(repository);
        var transactions = new TransactionTemplate(manager);
        return new TransactionalCareProjections(service, transactions);
    }

    /** Mỗi handler chạy trong một transaction mới, như {@code carePaymentReceipts} ở trên. */
    private record TransactionalCareProjections(CareProjectionService service, TransactionTemplate transactions)
            implements ReactToCareProjectionUseCase {
        @Override public void onAdmissionDepositRequested(AdmissionDepositRequestedCommand c) {
            transactions.executeWithoutResult(status -> service.onAdmissionDepositRequested(c));
        }
        @Override public void onAdmissionStarted(AdmissionStartedCommand c) {
            transactions.executeWithoutResult(status -> service.onAdmissionStarted(c));
        }
        @Override public void onAdmissionClosed(AdmissionClosedCommand c) {
            transactions.executeWithoutResult(status -> service.onAdmissionClosed(c));
        }
        @Override public void onSurgeryReady(SurgeryReadyCommand c) {
            transactions.executeWithoutResult(status -> service.onSurgeryReady(c));
        }
        @Override public void onSurgeryCancelled(SurgeryCancelledNoticeCommand c) {
            transactions.executeWithoutResult(status -> service.onSurgeryCancelled(c));
        }
    }
}
