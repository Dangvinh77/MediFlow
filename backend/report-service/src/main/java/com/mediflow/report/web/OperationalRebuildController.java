package com.mediflow.report.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.request.AdvanceOperationalReplayRequest;
import com.mediflow.report.application.dto.response.OperationalPublicationCandidateDTO;
import com.mediflow.report.application.dto.response.OperationalReplayProgress;
import com.mediflow.report.application.port.in.GetOperationalPublicationCandidateUseCase;
import com.mediflow.report.application.port.in.ReplayOperationalProjectionUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports/operations/rebuilds")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2", "mediflow.report.operational-publication.enabled"}, havingValue="true")
@PreAuthorize("hasRole('ADMIN')")
public class OperationalRebuildController {
    private final ReplayOperationalProjectionUseCase replay;
    private final GetOperationalPublicationCandidateUseCase candidates;
    public OperationalRebuildController(ReplayOperationalProjectionUseCase replay, GetOperationalPublicationCandidateUseCase candidates) {
        this.replay = replay; this.candidates = candidates;
    }
    @PostMapping
    public ResponseEntity<ApiResponse<OperationalReplayProgress>> start(HttpServletRequest http) {
        var progress = replay.start();
        return ResponseEntity.status(201).header("Location", "/api/v1/reports/operations/rebuilds/" + progress.generationId())
                .header("Cache-Control", "no-store").body(ApiResponse.ok(progress, http.getHeader(JwtClaims.HEADER_CORRELATION_ID)));
    }
    @PostMapping("/{id}/batches")
    public ResponseEntity<ApiResponse<OperationalReplayProgress>> advance(@PathVariable UUID id,
            @Valid @RequestBody AdvanceOperationalReplayRequest request, HttpServletRequest http) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.ok(replay.advance(id, request.batchSize()),
                http.getHeader(JwtClaims.HEADER_CORRELATION_ID)));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OperationalPublicationCandidateDTO>> candidate(@PathVariable UUID id, HttpServletRequest http) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.ok(candidates.candidate(id),
                http.getHeader(JwtClaims.HEADER_CORRELATION_ID)));
    }
}
