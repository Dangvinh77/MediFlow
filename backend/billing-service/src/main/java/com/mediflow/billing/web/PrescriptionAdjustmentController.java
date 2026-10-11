package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.response.PrescriptionAdjustmentDTO;
import com.mediflow.billing.application.port.in.GetPrescriptionAdjustmentUseCase;
import com.mediflow.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/billing/prescription-adjustments")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.prescription-charge-consumer.enabled"}, havingValue = "true")
public class PrescriptionAdjustmentController {
    private final GetPrescriptionAdjustmentUseCase adjustments;
    public PrescriptionAdjustmentController(GetPrescriptionAdjustmentUseCase adjustments) { this.adjustments = adjustments; }
    @GetMapping("/{id}/refunds-due")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ApiResponse<PrescriptionAdjustmentDTO> get(@PathVariable UUID id) { return ApiResponse.ok(adjustments.get(id)); }
}
