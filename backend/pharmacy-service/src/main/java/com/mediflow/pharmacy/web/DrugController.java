package com.mediflow.pharmacy.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.request.AdjustStockRequest;
import com.mediflow.pharmacy.application.dto.request.CreateDrugRequest;
import com.mediflow.pharmacy.application.dto.response.DrugDTO;
import com.mediflow.pharmacy.application.port.in.ManageDrugUseCase;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for drug catalogue and stock-management operations.
 *
 * <p>The controller owns HTTP validation and authorization only. Catalogue, stock and audit rules
 * remain inside the application and domain layers.</p>
 */
@RestController
@RequestMapping("/api/v1/pharmacy/drugs")
@RequiredArgsConstructor
public class DrugController {

    private final ManageDrugUseCase manageDrugUseCase;

    /**
     * Searches drugs using bounded pagination.
     *
     * @param keyword optional drug-name keyword
     * @param page optional zero-based page
     * @param size optional bounded page size
     * @param correlationId trace id propagated by the gateway
     * @return matching drug page
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PHARMACIST')")
    public ResponseEntity<ApiResponse<PageResult<DrugDTO>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(
                    value = JwtClaims.HEADER_CORRELATION_ID,
                    required = false) String correlationId) {
        PageResult<DrugDTO> result = manageDrugUseCase.search(keyword, PageQuery.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(result, normalizeCorrelationId(correlationId)));
    }

    /**
     * Reads one drug by its stable identity.
     *
     * @param id drug identity
     * @param correlationId trace id propagated by the gateway
     * @return current drug details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'PHARMACIST')")
    public ResponseEntity<ApiResponse<DrugDTO>> getById(
            @PathVariable UUID id,
            @RequestHeader(
                    value = JwtClaims.HEADER_CORRELATION_ID,
                    required = false) String correlationId) {
        return ResponseEntity.ok(ApiResponse.ok(
                manageDrugUseCase.getById(id), normalizeCorrelationId(correlationId)));
    }

    /**
     * Adds a validated drug to the catalogue.
     *
     * @param request catalogue and initial-stock data
     * @param correlationId trace id propagated by the gateway
     * @return HTTP 201 with the new resource location
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<ApiResponse<DrugDTO>> create(
            @Valid @RequestBody CreateDrugRequest request,
            @RequestHeader(
                    value = JwtClaims.HEADER_CORRELATION_ID,
                    required = false) String correlationId) {
        DrugDTO created = manageDrugUseCase.create(request);
        URI location = URI.create("/api/v1/pharmacy/drugs/" + created.drugId());
        return ResponseEntity.created(location)
                .body(ApiResponse.ok(created, normalizeCorrelationId(correlationId)));
    }

    /**
     * Applies an audited stock delta to one drug.
     *
     * @param id drug identity
     * @param request non-zero stock delta and operator reason
     * @param authentication signed operator identity
     * @param correlationId trace id propagated by the gateway
     * @return drug snapshot after adjustment
     */
    @PutMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'PHARMACIST')")
    public ResponseEntity<ApiResponse<DrugDTO>> adjustStock(
            @PathVariable UUID id,
            @Valid @RequestBody AdjustStockRequest request,
            Authentication authentication,
            @RequestHeader(
                    value = JwtClaims.HEADER_CORRELATION_ID,
                    required = false) String correlationId) {
        ActorIdentity actor = parseActor(authentication);
        String traceId = normalizeCorrelationId(correlationId);
        DrugDTO updated = manageDrugUseCase.adjustStock(
                id, request, operationalActorId(actor), traceId);
        return ResponseEntity.ok(ApiResponse.ok(updated, traceId));
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

    /** Resolves the signed staff identity allowed to own a stock-audit operation. */
    private UUID operationalActorId(ActorIdentity actor) {
        try {
            return actor.auditActorId();
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
