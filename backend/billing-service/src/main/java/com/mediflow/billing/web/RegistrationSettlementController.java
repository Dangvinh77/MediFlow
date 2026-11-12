package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.response.RegistrationSettlementDTO;
import com.mediflow.billing.application.port.in.ListRegistrationSettlementsUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.security.JwtClaims;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/billing/registration-settlements")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.scheduled-payments.enabled"}, havingValue = "true")
public class RegistrationSettlementController {
    private final ListRegistrationSettlementsUseCase settlements;

    public RegistrationSettlementController(ListRegistrationSettlementsUseCase settlements) { this.settlements = settlements; }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<PageResult<RegistrationSettlementDTO>>> list(@RequestParam(required = false) String kind,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String suppliedCorrelation) {
        String correlation = suppliedCorrelation == null || suppliedCorrelation.isBlank() ? UUID.randomUUID().toString() : suppliedCorrelation;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.ok(settlements.list(kind, PageQuery.of(page, size)), correlation));
    }
}
