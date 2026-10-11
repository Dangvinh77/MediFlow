package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.request.AllocateAdmissionDepositRequest;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.DepositAllocatedPayload;
import com.mediflow.billing.application.port.in.AllocateAdmissionDepositUseCase;
import com.mediflow.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/accounts")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.settlement-preview.enabled",
        "mediflow.billing.deposit-allocation.enabled"}, havingValue = "true")
public class AdmissionDepositAllocationController {
    private final AllocateAdmissionDepositUseCase allocations;
    public AdmissionDepositAllocationController(AllocateAdmissionDepositUseCase allocations) { this.allocations = allocations; }

    @PostMapping("/{id}/deposit-allocations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ApiResponse<DepositAllocatedPayload> allocate(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody AllocateAdmissionDepositRequest request, Authentication actor) {
        return ApiResponse.ok(allocations.allocate(request.command(id, UUID.fromString(actor.getName()), key)));
    }
}
