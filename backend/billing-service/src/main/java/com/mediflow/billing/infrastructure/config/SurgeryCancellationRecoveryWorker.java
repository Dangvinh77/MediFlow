package com.mediflow.billing.infrastructure.config;

import com.mediflow.billing.application.port.in.ProcessSurgeryCancellationUseCase;
import org.springframework.scheduling.annotation.Scheduled;

public final class SurgeryCancellationRecoveryWorker {
    private final ProcessSurgeryCancellationUseCase cancellations;
    public SurgeryCancellationRecoveryWorker(ProcessSurgeryCancellationUseCase cancellations) { this.cancellations = cancellations; }
    @Scheduled(fixedDelayString = "${mediflow.billing.surgery-cancellation-recovery.delay-ms:5000}")
    public void recover() { cancellations.recoverPending(); }
}
