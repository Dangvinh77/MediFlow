package com.mediflow.pharmacy.infrastructure.scheduling;

import com.mediflow.pharmacy.application.port.in.RecoverPrescriptionRevisionsUseCase;
import com.mediflow.pharmacy.application.port.out.PrescriptionRevisionOperationPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Retries PENDING/PREPARED/APPLIED/ABORTING receipts until Billing and Pharmacy agree. */
public class PrescriptionRevisionRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(PrescriptionRevisionRecoveryScheduler.class);
    private final PrescriptionRevisionOperationPort operations;
    private final RecoverPrescriptionRevisionsUseCase recovery;

    public PrescriptionRevisionRecoveryScheduler(PrescriptionRevisionOperationPort operations,
            RecoverPrescriptionRevisionsUseCase recovery) {
        this.operations = operations;
        this.recovery = recovery;
    }

    @Scheduled(fixedDelayString = "${mediflow.pharmacy.revision.reconciliation-ms:15000}")
    public void reconcile() {
        for (UUID operationId : operations.findActiveIds(50)) {
            try {
                recovery.recover(operationId);
            } catch (RuntimeException pending) {
                log.warn("Prescription revision {} remains pending reconciliation", operationId);
            }
        }
    }
}
