package com.mediflow.clinical.web;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.PaymentLockCommand;
import com.mediflow.clinical.application.dto.request.PaymentResolutionCommand;
import com.mediflow.clinical.application.dto.request.RegistrationCancellationCommand;
import com.mediflow.clinical.application.dto.request.RegistrationCreateRequest;
import com.mediflow.clinical.application.dto.request.RevisionCommand;
import com.mediflow.clinical.application.dto.response.OutpatientRegistrationDTO;
import com.mediflow.clinical.application.dto.response.ReservationPaymentContext;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.service.OutpatientRegistrationApplicationService;
import com.mediflow.clinical.infrastructure.security.ClinicalServicePrincipal;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.common.security.JwtClaims;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class OutpatientRegistrationController {
    private static final String BASE_PATH = "/api/v2/outpatient-registrations";
    private final OutpatientRegistrationApplicationService registrations;
    private final CorrelationIdProvider correlationIds;

    public OutpatientRegistrationController(OutpatientRegistrationApplicationService registrations,
            CorrelationIdProvider correlationIds) {
        this.registrations = registrations;
        this.correlationIds = correlationIds;
    }

    @PostMapping(BASE_PATH)
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<OutpatientRegistrationDTO>> create(
            @Valid @RequestBody RegistrationCreateRequest request,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireCorrelation(correlationId);
        IdempotentCommandPort.Result<OutpatientRegistrationDTO> result = registrations.create(request, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()));
        if (result.location() != null) response.location(URI.create(result.location()));
        return response.body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }

    @GetMapping(BASE_PATH + "/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiResponse<PageResult<OutpatientRegistrationDTO>>> mine(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
            HttpServletRequest request) {
        requireOnlyParameters(request, Set.of("page", "size"));
        return ResponseEntity.ok(ApiResponse.ok(registrations.mine(PageQuery.of(page, size)),
                correlationIds.currentOrCreate().toString()));
    }

    @GetMapping(BASE_PATH + "/follow-up-sources")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<PageResult<com.mediflow.clinical.application.dto.response.FollowUpSourceSummary>>> followUpSources(
            @RequestParam UUID patientId, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size, HttpServletRequest request) {
        requireOnlyParameters(request, Set.of("patientId", "page", "size"));
        return ResponseEntity.ok(ApiResponse.ok(registrations.followUpSources(patientId, PageQuery.of(page, size)),
                correlationIds.currentOrCreate().toString()));
    }

    @GetMapping(BASE_PATH + "/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE','DOCTOR','PATIENT')")
    public ResponseEntity<ApiResponse<OutpatientRegistrationDTO>> get(@PathVariable UUID id,
            HttpServletRequest request) {
        requireOnlyParameters(request, Set.of());
        return ResponseEntity.ok(ApiResponse.ok(registrations.get(id), correlationIds.currentOrCreate().toString()));
    }

    @PutMapping(BASE_PATH + "/{id}/arrival")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<OutpatientRegistrationDTO>> arrive(@PathVariable UUID id,
            @Valid @RequestBody RevisionCommand command,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireCorrelation(correlationId);
        return idempotent(registrations.arrive(id, command, idempotencyKey));
    }

    @PutMapping(BASE_PATH + "/{id}/cancellation")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE','DOCTOR')")
    public ResponseEntity<ApiResponse<OutpatientRegistrationDTO>> cancel(@PathVariable UUID id,
            @Valid @RequestBody RegistrationCancellationCommand command,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        requireCorrelation(correlationId);
        return idempotent(registrations.cancel(id, command, idempotencyKey));
    }

    @GetMapping("/api/v2/clinical/reservations/{id}/payment-context")
    @PreAuthorize("hasRole('SYSTEM') and principal instanceof T(com.mediflow.clinical.infrastructure.security.ClinicalServicePrincipal) and principal.serviceName() == 'billing-service'")
    public ResponseEntity<ApiResponse<ReservationPaymentContext>> paymentContext(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(registrations.paymentContext(id),
                correlationIds.currentOrCreate().toString()));
    }

    @PostMapping("/api/v2/clinical/reservations/{id}/payment-locks")
    @PreAuthorize("hasRole('SYSTEM') and principal instanceof T(com.mediflow.clinical.infrastructure.security.ClinicalServicePrincipal) and principal.serviceName() == 'billing-service'")
    public ResponseEntity<ApiResponse<com.mediflow.clinical.application.dto.request.PaymentLease>> acquirePayment(
            @PathVariable UUID id, @Valid @RequestBody PaymentLockCommand command) {
        return ResponseEntity.ok(ApiResponse.ok(registrations.acquirePayment(id, command),
                correlationIds.currentOrCreate().toString()));
    }

    @PutMapping("/api/v2/clinical/reservations/{id}/payment-resolution")
    @PreAuthorize("hasRole('SYSTEM') and principal instanceof T(com.mediflow.clinical.infrastructure.security.ClinicalServicePrincipal) and principal.serviceName() == 'billing-service'")
    public ResponseEntity<ApiResponse<OutpatientRegistrationDTO>> resolvePayment(
            @PathVariable UUID id, @Valid @RequestBody PaymentResolutionCommand command) {
        if (!id.equals(command.reservationId())) {
            throw new BusinessRuleException("IDEMPOTENCY_CONFLICT", "Reservation path and body do not match");
        }
        return ResponseEntity.ok(ApiResponse.ok(registrations.resolvePayment(command),
                correlationIds.currentOrCreate().toString()));
    }

    private ResponseEntity<ApiResponse<OutpatientRegistrationDTO>> idempotent(
            IdempotentCommandPort.Result<OutpatientRegistrationDTO> result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(result.status())
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()));
        if (result.location() != null) response.location(URI.create(result.location()));
        return response.body(ApiResponse.ok(result.data(), correlationIds.currentOrCreate().toString()));
    }

    private static void requireCorrelation(String value) {
        if (value == null || value.isBlank() || value.length() > 120
                || value.chars().anyMatch(character -> character < 33 || character > 126)) {
            throw new BusinessRuleException("INVALID_REQUEST", "X-Correlation-Id must be printable and at most 120 characters");
        }
    }

    private static void requireOnlyParameters(HttpServletRequest request, Set<String> allowed) {
        if (!allowed.containsAll(request.getParameterMap().keySet())) {
            throw new BusinessRuleException("INVALID_REQUEST", "Unsupported query parameter");
        }
    }
}
