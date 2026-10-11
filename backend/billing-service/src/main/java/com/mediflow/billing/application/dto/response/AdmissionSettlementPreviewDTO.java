package com.mediflow.billing.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.mediflow.billing.domain.model.AdmissionLedgerSnapshot;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.SettlementOutcome;

/** Diagnostic snapshot only. No arbitrary amount, debt approval or completed settlement is accepted. */
public record AdmissionSettlementPreviewDTO(UUID accountId, UUID admissionId, UUID departmentId,
        String currency, AccountStatus accountStatus, Instant chargeClosedAt, Instant observedAt,
        BigDecimal grossAmount, BigDecimal insuranceAmount, BigDecimal patientLiability,
        BigDecimal completedPayments, BigDecimal completedRefunds, BigDecimal completedReversals,
        BigDecimal netAllocatedPayments, BigDecimal unappliedNetPayments, BigDecimal unallocatedDeposit,
        BigDecimal balance, SettlementOutcome estimatedOutcome, boolean snapshotOnly, boolean finalSettlement) {
    public static AdmissionSettlementPreviewDTO from(AdmissionLedgerSnapshot snapshot, AdmissionLedgerSnapshot.Totals totals) {
        return new AdmissionSettlementPreviewDTO(snapshot.accountId(), snapshot.admissionId(), snapshot.departmentId(),
                snapshot.currency(), snapshot.accountStatus(), snapshot.chargeClosedAt(), snapshot.observedAt(),
                totals.grossAmount(), totals.insuranceAmount(), totals.patientLiability(), totals.completedPayments(),
                totals.completedRefunds(), totals.completedReversals(), totals.netAllocatedPayments(),
                totals.unappliedNetPayments(), totals.unallocatedDeposit(), totals.balance(), totals.estimatedOutcome(), true, false);
    }
}
