package com.mediflow.lab.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.lab.application.dto.response.LabOrderLifecycleDTO;
import com.mediflow.lab.application.port.in.LookupLabOrderLifecycleUseCase;
import com.mediflow.lab.infrastructure.security.LabServicePrincipal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/lab/source-orders")
public class LabOrderLifecycleController {
    private final LookupLabOrderLifecycleUseCase lifecycle;
    public LabOrderLifecycleController(LookupLabOrderLifecycleUseCase lifecycle) { this.lifecycle = lifecycle; }

    @GetMapping("/{sourceOrderId}/lifecycle")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<LabOrderLifecycleDTO>> lookup(@PathVariable UUID sourceOrderId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId, Authentication authentication,
            jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        if (!(authentication.getPrincipal() instanceof LabServicePrincipal principal)
                || !principal.subject().equals("clinical-service")) throw new org.springframework.security.access.AccessDeniedException("Clinical service credential required");
        if (correlationId == null || correlationId.isBlank() || correlationId.length() > 120 || !request.getParameterMap().isEmpty())
            return ResponseEntity.badRequest().body(ApiResponse.fail(ApiResponse.ApiError.of("INVALID_REQUEST", "Invalid lifecycle query or correlation"), correlationId));
        response.setHeader("X-Correlation-Id", correlationId);
        return ResponseEntity.ok(ApiResponse.ok(lifecycle.lookup(sourceOrderId), correlationId));
    }
}
