package com.mediflow.surgery.web;

import com.mediflow.common.api.*;
import com.mediflow.surgery.application.dto.response.*;
import com.mediflow.surgery.application.port.in.QuerySurgeryCasesUseCase;
import com.mediflow.surgery.infrastructure.correlation.CorrelationIdRequestAttribute;
import com.mediflow.surgery.infrastructure.security.SurgeryAuthenticationDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping("/api/v1/surgery/cases")
@ConditionalOnProperty(prefix = "mediflow.features.surgery", name = "enabled", havingValue = "true")
public class SurgeryQueryController {
    private final QuerySurgeryCasesUseCase queries;
    public SurgeryQueryController(QuerySurgeryCasesUseCase queries) { this.queries = queries; }

    @GetMapping("/{surgeryCaseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE')")
    public ApiResponse<SurgeryCaseDetails> detail(@PathVariable UUID surgeryCaseId,
            Authentication authentication, HttpServletRequest request) {
        String correlation = correlation(request);
        return ApiResponse.ok(queries.detail(surgeryCaseId, viewer(authentication), correlation), correlation);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'DOCTOR', 'NURSE')")
    public ApiResponse<PageResult<SurgeryCaseBoardItem>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) @jakarta.validation.constraints.Pattern(
                    regexp = "REQUESTED|PREOP_IN_PROGRESS|READY|SCHEDULED|IN_PROGRESS|COMPLETED|CANCELLED") String status,
            @RequestParam(required = false) Instant requestedFrom,
            @RequestParam(required = false) Instant requestedUntil,
            @RequestParam(required = false) Instant scheduledFrom,
            @RequestParam(required = false) Instant scheduledUntil,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication, HttpServletRequest request) {
        String correlation = correlation(request);
        var filter = QuerySurgeryCasesUseCase.Filter.fromRaw(departmentId, status, requestedFrom,
                requestedUntil, scheduledFrom, scheduledUntil, PageQuery.of(page, size));
        return ApiResponse.ok(queries.list(filter, viewer(authentication), correlation), correlation);
    }

    private static QuerySurgeryCasesUseCase.Viewer viewer(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID account)
                || !(authentication.getDetails() instanceof SurgeryAuthenticationDetails identity)
                || !account.equals(identity.accountId())) throw new AccessDeniedException("Verified identity required");
        var role = authentication.getAuthorities().stream().map(authority -> authority.getAuthority())
                .filter(value -> value.matches("ROLE_(ADMIN|MANAGER|DOCTOR|NURSE)"))
                .findFirst().orElseThrow(() -> new AccessDeniedException("Surgery reader role required"));
        return new QuerySurgeryCasesUseCase.Viewer(account, identity.staffId(),
                QuerySurgeryCasesUseCase.ReadRole.valueOf(role.substring(5)));
    }
    private static String correlation(HttpServletRequest request) {
        UUID value = CorrelationIdRequestAttribute.read(request);
        return value == null ? UUID.randomUUID().toString() : value.toString();
    }
}
