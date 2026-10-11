package com.mediflow.billing.web;

import java.util.UUID;
import com.mediflow.billing.application.dto.response.AdmissionSettlementPreviewDTO;
import com.mediflow.billing.application.port.in.GetAdmissionSettlementPreviewUseCase;
import com.mediflow.common.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/accounts")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.settlement-preview.enabled"}, havingValue = "true")
public class AdmissionSettlementPreviewController {
    private final GetAdmissionSettlementPreviewUseCase preview;
    public AdmissionSettlementPreviewController(GetAdmissionSettlementPreviewUseCase preview) { this.preview = preview; }
    @GetMapping("/{id}/settlement-preview")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ApiResponse<AdmissionSettlementPreviewDTO> get(@PathVariable UUID id) { return ApiResponse.ok(preview.get(id)); }
}
