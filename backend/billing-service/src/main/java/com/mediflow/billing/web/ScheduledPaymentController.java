package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.command.ScheduledPaymentCommand;
import com.mediflow.billing.application.dto.request.CloseScheduledPaymentRequest;
import com.mediflow.billing.application.dto.request.ScheduledPaymentRequest;
import com.mediflow.billing.application.dto.response.RegistrationPaymentRequestDTO;
import com.mediflow.billing.application.dto.response.ScheduledPaymentResult;
import com.mediflow.billing.application.port.in.DiscoverRegistrationPaymentRequestUseCase;
import com.mediflow.billing.application.port.out.ScheduledPaymentTransactionsPort.ClosePaymentCommand;
import com.mediflow.billing.application.service.ScheduledPaymentCoordinator;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/billing")
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.scheduled-payments.enabled"},
        havingValue = "true")
public class ScheduledPaymentController {
    private final ScheduledPaymentCoordinator payments;
    private final DiscoverRegistrationPaymentRequestUseCase discovery;

    public ScheduledPaymentController(ScheduledPaymentCoordinator payments,
            DiscoverRegistrationPaymentRequestUseCase discovery) {
        this.payments = payments;
        this.discovery = discovery;
    }

    @GetMapping("/payment-requests/by-registration")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<RegistrationPaymentRequestDTO>> byRegistration(HttpServletRequest request,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String suppliedCorrelation) {
        String correlation = correlation(suppliedCorrelation);
        String value = exactSingleValue(request.getParameterMap(), List.of("registrationId"));
        if (value == null) return invalid(correlation, "Exactly one registrationId is required");
        UUID registrationId;
        try { registrationId = UUID.fromString(value); }
        catch (IllegalArgumentException malformed) { return invalid(correlation, "registrationId must be a UUID"); }
        return ResponseEntity.ok().header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.ok(discovery.discover(registrationId), correlation));
    }

    @PostMapping("/payment-requests/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER')")
    public ResponseEntity<ApiResponse<ScheduledPaymentResult>> pay(@PathVariable UUID id,
            @Valid @RequestBody ScheduledPaymentRequest request, Principal principal,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String suppliedCorrelation) {
        String correlation = correlation(suppliedCorrelation);
        UUID actor = actor(principal);
        var command = ScheduledPaymentCommand.fromWire(request);
        var result = payments.pay(id, command, actor, idempotencyKey, correlation, java.time.Instant.now());
        if (result.isClosedNotPaid()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                    .body(ApiResponse.fail(ApiResponse.ApiError.of("PAYMENT_OPERATION_CLOSED",
                            "Payment operation is closed without payment"), correlation));
        }
        HttpStatus status = result.isPreparing()
                ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.ok(result, correlation));
    }

    @GetMapping("/payment-operations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CASHIER','SYSTEM')")
    public ResponseEntity<ApiResponse<ScheduledPaymentResult>> operation(@PathVariable UUID id,
            HttpServletRequest request, Authentication authentication,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String suppliedCorrelation) {
        String correlation = correlation(suppliedCorrelation);
        ScheduledPaymentResult result;
        if (hasRole(authentication, "SYSTEM")) {
            String[] binding = exactValues(request.getParameterMap(), List.of("registrationId", "reservationId"));
            if (binding == null) return invalid(correlation, "Internal lookup requires exact registrationId and reservationId");
            try {
                result = payments.currentForClinical(id, UUID.fromString(binding[0]), UUID.fromString(binding[1]));
            } catch (IllegalArgumentException malformed) {
                return invalid(correlation, "Internal binding values must be UUIDs");
            }
        } else {
            if (!request.getParameterMap().isEmpty()) return invalid(correlation, "Human lookup does not accept binding query parameters");
            result = payments.currentForHuman(id, actor(authentication));
        }
        return ResponseEntity.ok().header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.ok(result, correlation));
    }

    @PostMapping("/payment-operations/{id}/close-if-unpaid")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<ScheduledPaymentResult>> closeIfUnpaid(@PathVariable UUID id,
            @Valid @RequestBody CloseScheduledPaymentRequest request, Authentication authentication,
            @RequestHeader(value = JwtClaims.HEADER_CORRELATION_ID, required = false) String suppliedCorrelation) {
        requireClinical(authentication);
        String correlation = correlation(suppliedCorrelation);
        var command = new ClosePaymentCommand(request.registrationId(), request.reservationId(),
                request.fenceToken(), request.reason());
        var result = payments.closeIfUnpaid(id, command, java.time.Instant.now());
        return ResponseEntity.ok().header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.ok(result, correlation));
    }

    private static String[] exactValues(Map<String, String[]> params, List<String> names) {
        if (!params.keySet().equals(new java.util.HashSet<>(names))) return null;
        String[] result = new String[names.size()];
        for (int i = 0; i < names.size(); i++) {
            String[] values = params.get(names.get(i));
            if (values == null || values.length != 1 || values[0] == null || values[0].isBlank()) return null;
            result[i] = values[0];
        }
        return result;
    }

    private static String exactSingleValue(Map<String, String[]> params, List<String> names) {
        String[] values = exactValues(params, names);
        return values == null ? null : values[0];
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    private static void requireClinical(Authentication authentication) {
        if (!hasRole(authentication, "SYSTEM") || !"clinical-service".equals(authentication.getName())) {
            throw new AccessDeniedException("Only Clinical service can reconcile a payment operation");
        }
    }

    private static UUID actor(Principal principal) {
        try { return UUID.fromString(principal.getName()); }
        catch (RuntimeException invalid) { throw new AccessDeniedException("Verified account UUID is required"); }
    }

    private static String correlation(String supplied) {
        String result = supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
        if (result.length() > 120) throw new BusinessRuleException("INVALID_CORRELATION_ID", "Correlation id is too long");
        return result;
    }

    private static <T> ResponseEntity<ApiResponse<T>> invalid(String correlation, String message) {
        return ResponseEntity.badRequest().header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.fail(ApiResponse.ApiError.of("INVALID_REQUEST", message), correlation));
    }
}
