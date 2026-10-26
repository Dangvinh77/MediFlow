package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.command.ScheduledPaymentCommand;
import com.mediflow.billing.application.dto.response.ScheduledPaymentResult;
import com.mediflow.billing.application.service.ScheduledPaymentCoordinator;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.exception.BusinessRuleException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.scheduled-payments.enabled",
        "mediflow.billing.outpatient-quotes.enabled", "mediflow.billing.zero-payable-eligibility.enabled"}, havingValue = "true")
public class ScheduledZeroEligibilityController {
    private final ScheduledPaymentCoordinator payments;
    public ScheduledZeroEligibilityController(ScheduledPaymentCoordinator payments) { this.payments = payments; }

    @PostMapping("/api/v2/billing/payment-requests/{id}/zero-payable-eligibility")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<ScheduledPaymentResult>> confirm(@PathVariable UUID id,
            @Valid @RequestBody Command command, Authentication actor,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader("X-Correlation-Id") String correlation) {
        if (correlation.isBlank() || correlation.length() > 120 || key.isBlank() || key.length() > 120
                || correlation.chars().anyMatch(c -> c < 33 || c > 126) || key.chars().anyMatch(c -> c < 33 || c > 126))
            throw new BusinessRuleException("INVALID_REQUEST", "Printable command headers are required");
        var result = payments.pay(id, ScheduledPaymentCommand.zero(command.reservationId(), command.expectedReservationRevision(), command.quoteId()),
                UUID.fromString(actor.getName()), key, correlation, Instant.now());
        int status = result.isPreparing() ? 202
                : result.isClosedNotPaid() ? 409 : 200;
        if (result.isClosedNotPaid()) {
            return ResponseEntity.status(status).header("X-Correlation-Id", correlation)
                    .body(ApiResponse.fail(ApiResponse.ApiError.of("PAYMENT_OPERATION_CLOSED",
                            "Payment operation is closed without eligibility"), correlation));
        }
        return ResponseEntity.status(status).header("X-Correlation-Id", correlation).body(ApiResponse.ok(result, correlation));
    }

    public record Command(@NotNull UUID reservationId, @Positive int expectedReservationRevision, @NotNull UUID quoteId) {}
}
