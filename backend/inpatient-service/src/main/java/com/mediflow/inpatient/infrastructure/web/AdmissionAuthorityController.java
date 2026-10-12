package com.mediflow.inpatient.infrastructure.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.inpatient.application.dto.response.AdmissionLookupDTO;
import com.mediflow.inpatient.application.dto.response.PlacementLookupDTO;
import com.mediflow.inpatient.application.port.in.LookupAdmissionAuthorityUseCase;
import com.mediflow.inpatient.application.port.in.LookupPlacementAuthorityUseCase;
import com.mediflow.inpatient.infrastructure.correlation.CorrelationIdRequestAttribute;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
public class AdmissionAuthorityController {
    private final LookupAdmissionAuthorityUseCase admissions;
    private final LookupPlacementAuthorityUseCase placements;
    public AdmissionAuthorityController(LookupAdmissionAuthorityUseCase admissions,
                                        LookupPlacementAuthorityUseCase placements) {
        this.admissions = admissions;
        this.placements = placements;
    }

    @GetMapping("/api/v1/inpatient/admissions/{id}/lookup")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE')")
    public ApiResponse<AdmissionLookupDTO> lookup(@PathVariable UUID id, HttpServletRequest request) {
        return ApiResponse.ok(admissions.lookup(id),correlation(request));
    }

    @GetMapping("/api/v1/inpatient/admissions/{id}/placement-lookup")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE')")
    public ApiResponse<PlacementLookupDTO> placement(@PathVariable UUID id, HttpServletRequest request) {
        return ApiResponse.ok(placements.lookup(id), correlation(request));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> unavailable(DataAccessException failure,HttpServletRequest request) {
        return ResponseEntity.status(503).header(JwtClaims.HEADER_CORRELATION_ID,correlation(request))
                .body(ApiResponse.fail(ApiResponse.ApiError.of("INPATIENT_LOOKUP_UNAVAILABLE",
                        "Admission authority is unavailable"),correlation(request)));
    }

    private static String correlation(HttpServletRequest request) {
        UUID id = CorrelationIdRequestAttribute.read(request);
        return id == null ? null : id.toString();
    }
}
