package com.mediflow.billing.application.port.out;

import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.InsuranceAdjustment;
import com.mediflow.billing.domain.model.Settlement;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AdmissionSettlementRepositoryPort {
    /** FOR UPDATE lookup by the exact ADMISSION episode; throws if no account was ever opened. */
    BillingAccount lockAccountByAdmission(UUID admissionId);
    /** FOR UPDATE lookup by account id; throws if not found. */
    BillingAccount lockAccountById(UUID accountId);
    void updateAccountStatus(BillingAccount account);
    /** Idempotent on {@code (accountId, decisionReference, adjustmentType)}; a repeat with a
     * different amount for the same decision conflicts rather than silently overwriting it. */
    void recordInsuranceAdjustment(InsuranceAdjustment adjustment);
    SettlementContext loadContext(UUID accountId);
    /** No PAYMENT_REQUEST_TARGET row: SETTLEMENT purpose carries no clearance target. */
    UUID createSettlementPaymentRequest(UUID accountId, BigDecimal amount, List<ChargeRemaining> charges, UUID createdBy);
    Settlement saveSettlement(Settlement settlement);

    record ChargeRemaining(UUID chargeId, BigDecimal remaining) { }

    /** {@code remainingCharges} sums exactly to {@code grossAmount - (completedPayments - completedRefunds)}
     * (the equations hold only while {@code cumulativeInsurance} is zero; see
     * BILLING_SETTLEMENT_INSURANCE_PAYMENT_REQUEST_UNSUPPORTED). */
    record SettlementContext(BigDecimal grossAmount, BigDecimal completedPayments, BigDecimal completedRefunds,
                             BigDecimal cumulativeInsurance, List<ChargeRemaining> remainingCharges,
                             int nextVersion, UUID previousSettlementId) { }
}
