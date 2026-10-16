package com.mediflow.report.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.response.FinancialArchiveRestoreDTO;
import com.mediflow.report.application.port.in.ManageFinancialSourceArchivesUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sensitive, operator-only local evidence. Not a producer import or financial publication route. */
@RestController
@RequestMapping("/api/v1/reports/financial/source-archives")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2", "mediflow.report.financial-archive.enabled"}, havingValue="true")
@org.springframework.context.annotation.Profile("!mvp-outpatient & !outpatient-mvp")
public class FinancialSourceArchiveController {
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private final ManageFinancialSourceArchivesUseCase archives;
    public FinancialSourceArchiveController(ManageFinancialSourceArchivesUseCase archives) { this.archives = archives; }

    @GetMapping(value="/{generationId}", produces=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> export(@PathVariable UUID generationId) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "attachment; filename=financial-source-" + generationId + ".json")
                .contentType(MediaType.APPLICATION_OCTET_STREAM).body(archives.exportArchive(generationId));
    }
    @PostMapping(consumes=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<FinancialArchiveRestoreDTO> restore(HttpServletRequest request, Authentication actor) throws IOException {
        // Bound BEFORE deserialization and do not accept filenames/URLs or persist request bytes in logs.
        if (request.getContentLengthLong() > MAX_BYTES) throw new com.mediflow.report.application.exception.ReportArchiveValidationException();
        byte[] bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new com.mediflow.report.application.exception.ReportArchiveValidationException();
        return ApiResponse.ok(archives.restoreArchive(bytes, UUID.fromString(actor.getName())), request.getHeader(JwtClaims.HEADER_CORRELATION_ID));
    }
}
