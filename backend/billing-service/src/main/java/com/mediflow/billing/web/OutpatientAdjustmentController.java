package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.response.OutpatientAdjustmentDTO;
import com.mediflow.billing.application.port.in.GetOutpatientAdjustmentUseCase;
import com.mediflow.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
@ConditionalOnProperty(name = "mediflow.billing.ledger.enabled", havingValue = "true")
public class OutpatientAdjustmentController {
    private final GetOutpatientAdjustmentUseCase adjustments;
    public OutpatientAdjustmentController(GetOutpatientAdjustmentUseCase adjustments) { this.adjustments = adjustments; }

    @GetMapping("/lab-adjustments/{id}/refunds-due")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ApiResponse<OutpatientAdjustmentDTO> lab(@PathVariable UUID id) {
        return ApiResponse.ok(adjustments.get("LAB_TEST", id));
    }

    @GetMapping("/exam-adjustments/{id}/refunds-due")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ApiResponse<OutpatientAdjustmentDTO> exam(@PathVariable UUID id) {
        return ApiResponse.ok(adjustments.get("EXAM", id));
    }
}
