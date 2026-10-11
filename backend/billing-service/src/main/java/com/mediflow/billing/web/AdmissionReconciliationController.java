package com.mediflow.billing.web;

import java.util.UUID;
import com.mediflow.billing.application.dto.command.ReconcileAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.SettlementReconciledPayload;
import com.mediflow.billing.application.port.in.ReconcileAdmissionUseCase;
import com.mediflow.common.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/accounts")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.settlement-preview.enabled",
        "mediflow.billing.reconciliation.enabled"}, havingValue = "true")
public class AdmissionReconciliationController {
    private final ReconcileAdmissionUseCase reconciliations;
    public AdmissionReconciliationController(ReconcileAdmissionUseCase reconciliations) { this.reconciliations = reconciliations; }
    @PostMapping("/{id}/settlement-reconciliations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ApiResponse<SettlementReconciledPayload> reconcile(@PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String key, Authentication actor) {
        return ApiResponse.ok(reconciliations.reconcile(new ReconcileAdmissionCommand(id, UUID.fromString(actor.getName()), key)));
    }
}
