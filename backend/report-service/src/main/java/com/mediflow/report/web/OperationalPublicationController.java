package com.mediflow.report.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.request.PublishOperationalSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackOperationalSnapshotRequest;
import com.mediflow.report.application.dto.response.OperationalPublicationDTO;
import com.mediflow.report.application.port.in.PublishOperationalSnapshotsUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/operations/publications")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2", "mediflow.report.operational-publication.enabled"}, havingValue="true")
public class OperationalPublicationController {
    private final PublishOperationalSnapshotsUseCase publications;
    public OperationalPublicationController(PublishOperationalSnapshotsUseCase publications) { this.publications = publications; }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OperationalPublicationDTO>> publish(@Valid @RequestBody PublishOperationalSnapshotRequest request,
            Authentication actor, HttpServletRequest http) {
        var result = publications.publish(request, UUID.fromString(actor.getName()));
        return ResponseEntity.status(201).header("Location", "/api/v1/reports/operations/rebuilds/" + result.generationId())
                .header("Cache-Control", "no-store").body(ApiResponse.ok(result, http.getHeader(JwtClaims.HEADER_CORRELATION_ID)));
    }
    @PostMapping("/rollbacks")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OperationalPublicationDTO>> rollback(@Valid @RequestBody RollbackOperationalSnapshotRequest request,
            Authentication actor, HttpServletRequest http) {
        var result = publications.rollback(request, UUID.fromString(actor.getName()));
        return ResponseEntity.status(201).header("Location", "/api/v1/reports/operations/rebuilds/" + result.generationId())
                .header("Cache-Control", "no-store").body(ApiResponse.ok(result, http.getHeader(JwtClaims.HEADER_CORRELATION_ID)));
    }
}
