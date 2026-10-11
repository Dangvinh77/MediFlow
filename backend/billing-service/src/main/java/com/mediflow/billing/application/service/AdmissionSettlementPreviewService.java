package com.mediflow.billing.application.service;

import java.util.UUID;
import com.mediflow.billing.application.dto.response.AdmissionSettlementPreviewDTO;
import com.mediflow.billing.application.port.in.GetAdmissionSettlementPreviewUseCase;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.common.exception.ResourceNotFoundException;

public class AdmissionSettlementPreviewService implements GetAdmissionSettlementPreviewUseCase {
    private final AdmissionLedgerReadPort ledger;
    public AdmissionSettlementPreviewService(AdmissionLedgerReadPort ledger) { this.ledger = ledger; }
    @Override public AdmissionSettlementPreviewDTO get(UUID accountId) {
        if (accountId == null) throw new IllegalArgumentException("accountId is required");
        var snapshot = ledger.read(accountId).orElseThrow(() -> new ResourceNotFoundException(
                "BILLING_ACCOUNT_NOT_FOUND", "Billing account not found"));
        if (!accountId.equals(snapshot.accountId())) throw new com.mediflow.billing.domain.exception.BillingRuleException(
                "BILLING_SETTLEMENT_LEDGER_INCONSISTENT", "Admission ledger evidence is inconsistent");
        return AdmissionSettlementPreviewDTO.from(snapshot, snapshot.reconcile());
    }
}
