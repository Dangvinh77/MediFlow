package com.mediflow.billing.web;

import com.mediflow.billing.application.dto.command.StartOutpatientExecutionCommand;
import com.mediflow.billing.application.dto.response.OutpatientExecutionStartDTO;
import com.mediflow.billing.application.port.in.StartOutpatientExecutionUseCase;
import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/financial-clearances")
@ConditionalOnProperty(name = "mediflow.billing.clearance-lookup.enabled", havingValue = "true")
public class OutpatientExecutionStartController {
    private final StartOutpatientExecutionUseCase starts;

    public OutpatientExecutionStartController(StartOutpatientExecutionUseCase starts) {
        this.starts = starts;
    }

    @PostMapping("/{id}/execution-starts")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<OutpatientExecutionStartDTO>> start(@PathVariable UUID id,
            @Valid @RequestBody StartOutpatientExecutionCommand command,
            @RequestHeader(JwtClaims.HEADER_CORRELATION_ID) String correlation,
            Authentication authentication) {
        if (correlation.isBlank() || correlation.length() > 120) {
            return ResponseEntity.badRequest().header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                    .body(ApiResponse.fail(ApiResponse.ApiError.of("INVALID_CORRELATION_ID", "Correlation identity required"), correlation));
        }
        String caller = authentication.getName();
        boolean callerOwnsType = "EXAM".equals(command.sourceType()) && "clinical-service".equals(caller)
                || "LAB_TEST".equals(command.sourceType()) && "lab-service".equals(caller);
        if (!callerOwnsType) throw new AccessDeniedException("Service identity does not own this source type");
        var result = starts.start(id, command, correlation);
        return ResponseEntity.ok().header(JwtClaims.HEADER_CORRELATION_ID, correlation)
                .body(ApiResponse.ok(result, correlation));
    }
}
