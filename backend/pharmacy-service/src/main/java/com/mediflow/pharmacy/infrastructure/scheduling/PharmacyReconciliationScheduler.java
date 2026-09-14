package com.mediflow.pharmacy.infrastructure.scheduling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mediflow.pharmacy.application.port.in.ReconcilePharmacyUseCase;
import com.mediflow.pharmacy.application.dto.response.ReconciliationFinding;
import com.mediflow.pharmacy.application.dto.response.ReconciliationReport;

import lombok.extern.slf4j.Slf4j;

/**
 * Periodically reports lifecycle/reservation mismatches in dry-run mode.
 *
 * <p>This adapter only logs prescription id, anomaly code and a short detail. It has no repair
 * dependency by design: unknown data must be reviewed before a migration or compensating use
 * case changes it.</p>
 */
@Component
@Slf4j
public class PharmacyReconciliationScheduler {

    private final ReconcilePharmacyUseCase reconciliation;
    private final int batchSize;

    /** Creates the dry-run scheduler with a bounded report size. */
    public PharmacyReconciliationScheduler(
            ReconcilePharmacyUseCase reconciliation,
            @Value("${mediflow.pharmacy.reservation.reconciliation-batch-size:100}") int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Reconciliation batch size must be positive");
        }
        this.reconciliation = reconciliation;
        this.batchSize = batchSize;
    }

    /** Runs a read-only reconciliation and emits only actionable anomaly metadata. */
    @Scheduled(cron = "${mediflow.pharmacy.reservation.reconciliation-cron:0 30 * * * *}")
    public void reconcileDryRun() {
        ReconciliationReport report = reconciliation.reconcileDryRun(batchSize);
        if (report.findings().isEmpty()) {
            return;
        }
        for (ReconciliationFinding finding : report.findings()) {
            log.warn("Pharmacy lifecycle anomaly prescriptionId={} type={} detail={}",
                    finding.prescriptionId(), finding.anomalyType(), finding.details());
        }
    }
}
