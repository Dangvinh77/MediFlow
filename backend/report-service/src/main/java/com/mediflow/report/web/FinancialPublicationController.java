package com.mediflow.report.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.request.PublishFinancialSnapshotRequest;
import com.mediflow.report.application.dto.request.RollbackFinancialSnapshotRequest;
import com.mediflow.report.application.dto.response.FinancialPublicationDTO;
import com.mediflow.report.application.port.in.PublishFinancialSnapshotsUseCase;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/reports/financial/publications")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2", "mediflow.report.financial-publication.enabled"}, havingValue="true")
public class FinancialPublicationController {
    private final PublishFinancialSnapshotsUseCase publications;
    public FinancialPublicationController(PublishFinancialSnapshotsUseCase publications) { this.publications = publications; }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialPublicationDTO>> publish(@Valid @RequestBody PublishFinancialSnapshotRequest request, Authentication actor, HttpServletRequest http) {
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(ApiResponse.ok(publications.publish(request, UUID.fromString(actor.getName())), http.getHeader(JwtClaims.HEADER_CORRELATION_ID)));
    }
    @PostMapping("/rollbacks")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialPublicationDTO>> rollback(@Valid @RequestBody RollbackFinancialSnapshotRequest request, Authentication actor, HttpServletRequest http) {
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(ApiResponse.ok(publications.rollback(request, UUID.fromString(actor.getName())), http.getHeader(JwtClaims.HEADER_CORRELATION_ID)));
    }
}
