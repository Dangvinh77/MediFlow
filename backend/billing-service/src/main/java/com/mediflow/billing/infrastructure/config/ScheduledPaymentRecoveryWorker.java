package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.service.ScheduledPaymentCoordinator;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.scheduled-payments.enabled"},
        havingValue = "true")
public class ScheduledPaymentRecoveryWorker {
    private final ScheduledPaymentCoordinator payments;
    private final Clock clock = Clock.systemUTC();

    public ScheduledPaymentRecoveryWorker(ScheduledPaymentCoordinator payments) { this.payments = payments; }

    @Scheduled(fixedDelayString = "${mediflow.billing.scheduled-payments.recovery-delay-ms:30000}",
            initialDelayString = "${mediflow.billing.scheduled-payments.recovery-initial-delay-ms:30000}")
    public void recover() {
        payments.recoverPending(clock.instant(), 50);
    }
}
