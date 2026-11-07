package com.mediflow.billing.web;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.billing.application.dto.response.PaymentHistoryItem;
import com.mediflow.billing.application.port.in.FindPaymentHistoryUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

/** Collected and refunded money from the ledger; the legacy invoice table is not written by this flow. */
@RestController
@RequestMapping("/api/v1/billing/patient/payments")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.payment-request-discovery.enabled"},
        havingValue = "true")
public class PaymentHistoryController {
    private final FindPaymentHistoryUseCase history;

    public PaymentHistoryController(FindPaymentHistoryUseCase history) {
        this.history = history;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public ResponseEntity<ApiResponse<PageResult<PaymentHistoryItem>>> find(
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(ApiResponse.ok(history.find(patientId, PageQuery.of(page, size))));
    }
}
