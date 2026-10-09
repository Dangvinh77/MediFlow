package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.response.SurgeryCancellationDTO;
import com.mediflow.billing.application.port.in.GetSurgeryCancellationUseCase;
import com.mediflow.common.api.ApiResponse;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/surgery-cancellations")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.surgery-charge-consumer.enabled"}, havingValue = "true")
public class SurgeryCancellationController {
    private final GetSurgeryCancellationUseCase cancellations;
    public SurgeryCancellationController(GetSurgeryCancellationUseCase cancellations) { this.cancellations = cancellations; }
    @GetMapping("/{id}/refunds-due")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ApiResponse<SurgeryCancellationDTO> get(@PathVariable UUID id) { return ApiResponse.ok(cancellations.get(id)); }
}
