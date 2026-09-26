package com.mediflow.pharmacy.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.request.CancelPrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.response.CancelPrescriptionResult;
import com.mediflow.pharmacy.application.dto.response.DispenseDTO;
import com.mediflow.pharmacy.application.dto.response.PrescriptionDTO;
import com.mediflow.pharmacy.application.port.in.CancelPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.CreatePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.DispensePrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.GetPrescriptionUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for prescription creation, lookup and terminal lifecycle operations.
 *
 * <p>The controller owns HTTP validation and authorization only. Locking, price snapshots,
 * reservations, dispensing and event publication remain inside application/domain services.</p>
 */
@RestController
@RequestMapping("/api/v1/pharmacy/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final CreatePrescriptionUseCase createPrescriptionUseCase;
    private final GetPrescriptionUseCase getPrescriptionUseCase;
    private final CancelPrescriptionUseCase cancelPrescriptionUseCase;
    private final DispensePrescriptionUseCase dispensePrescriptionUseCase;

    /**
     * Creates a prescription whose prices are resolved exclusively by the server.
     *
     * @param request prescription and requested drug lines without client prices
     * @param authentication signed doctor or administrator identity
     * @param correlationId trace id propagated by the gateway
     * @return HTTP 201 with the new prescription location
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<PrescriptionDTO>> create(
            @Valid @RequestBody CreatePrescriptionRequest request,
            Authentication authentication,
            @RequestHeader(
                    value = JwtClaims.HEADER_CORRELATION_ID,
                    required = false) String correlationId) {
        CreatePrescriptionCommand command = new CreatePrescriptionCommand(
                request, parseActor(authentication), correlationId);
        PrescriptionDTO created = createPrescriptionUseCase.create(command);
        URI location = URI.create(
                "/api/v1/pharmacy/prescriptions/" + created.prescriptionId());
        return ResponseEntity.created(location)
                .body(ApiResponse.ok(created, command.correlationId()));
    }

    /**
     * Cancels an active prescription and releases all remaining reservations atomically.
     *
     * @param prescriptionId prescription identity
     * @param request operator-visible cancellation reason
     * @param authentication signed doctor or administrator identity
     * @param correlationId trace id propagated by the gateway
     * @return resulting status and released-reservation count
     */
    @PutMapping("/{prescriptionId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<CancelPrescriptionResult>> cancel(
            @PathVariable UUID prescriptionId,
            @Valid @RequestBody CancelPrescriptionRequest request,
            Authentication authentication,
            @RequestHeader(
                    value = JwtClaims.HEADER_CORRELATION_ID,
                    required = false) String correlationId) {
        CancelPrescriptionCommand command = new CancelPrescriptionCommand(
                prescriptionId, parseActor(authentication), request.reason(), correlationId);
        CancelPrescriptionResult result = cancelPrescriptionUseCase.cancel(command);
        return ResponseEntity.ok(ApiResponse.ok(result, command.correlationId()));
    }

    /**
     * Reads prescription detail and its current dispense lifecycle.
     *
     * @param prescriptionId prescription identity
     * @param correlationId trace id propagated by the gateway
     * @return current prescription snapshot
     */
    @GetMapping("/{prescriptionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PHARMACIST')")
    public ResponseEntity<ApiResponse<PrescriptionDTO>> getById(
            @PathVariable UUID prescriptionId,
            @RequestHeader(
                    value = JwtClaims.HEADER_CORRELATION_ID,
                    required = false) String correlationId) {
        return ResponseEntity.ok(ApiResponse.ok(
                getPrescriptionUseCase.getPrescriptionById(prescriptionId),
                normalizeCorrelationId(correlationId)));
    }

    /**
     * Dispenses a prescription only after durable payment proof is available.
     *
     * @param prescriptionId prescription identity
     * @param authentication signed pharmacist or administrator identity
     * @param correlationId trace id propagated by the gateway
     * @return terminal dispense result
     */
    @PutMapping("/{prescriptionId}/dispense")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<ApiResponse<DispenseDTO>> dispense(
            @PathVariable UUID prescriptionId,
            Authentication authentication,
            @RequestHeader(
                    value = JwtClaims.HEADER_CORRELATION_ID,
                    required = false) String correlationId) {
        ActorIdentity actor = parseActor(authentication);
        String traceId = normalizeCorrelationId(correlationId);
        DispenseDTO result = dispensePrescriptionUseCase.dispense(
                prescriptionId, operationalActor(actor), traceId);
        return ResponseEntity.ok(ApiResponse.ok(result, traceId));
    }

    /** Resolves the verified principal without exposing malformed identities as HTTP 500. */
    private ActorIdentity parseActor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Không xác định được người dùng từ JWT subject");
        }
        if (authentication.getPrincipal() instanceof ActorIdentity actor) {
            return actor;
        }
        try {
            UUID accountId = UUID.fromString(authentication.getName());
            String role = authentication.getAuthorities().stream()
                    .findFirst()
                    .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                    .orElseThrow(() -> new AccessDeniedException("JWT không có role hợp lệ"));
            return new ActorIdentity(accountId, null, role);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException(
                    "JWT subject không phải mã người dùng hợp lệ", exception);
        }
    }

    /** Resolves the signed staff identity allowed to own a dispensing operation. */
    private ActorIdentity operationalActor(ActorIdentity actor) {
        try {
            actor.auditActorId();
            return actor;
        } catch (IllegalStateException exception) {
            throw new AccessDeniedException(
                    "JWT chưa cung cấp staffId đã xác thực cho thao tác nghiệp vụ", exception);
        }
    }

    /** Generates a trace id for direct calls that bypass the gateway. */
    private String normalizeCorrelationId(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }
}
