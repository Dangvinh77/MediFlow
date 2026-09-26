package com.mediflow.patient.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.dto.response.PatientLookupDTO;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.application.port.in.LookupPatientUseCase;
import com.mediflow.patient.application.port.out.CorrelationIdProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {
    private final GetPatientUseCase patients;
    private final LookupPatientUseCase lookup;
    private final CorrelationIdProvider correlationIds;

    public PatientController(GetPatientUseCase patients, LookupPatientUseCase lookup,
                             CorrelationIdProvider correlationIds) {
        this.patients = patients;
        this.lookup = lookup;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<PatientDTO>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(patients.getById(id), correlationId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<PageResult<PatientDTO>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.ok(patients.search(keyword, PageQuery.of(page, size)), correlationId()));
    }

    @GetMapping("/{id}/exists")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM')")
    public ResponseEntity<ApiResponse<PatientLookupDTO>> exists(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(lookup.exists(id), correlationId()));
    }

    private String correlationId() { return correlationIds.currentOrCreate().toString(); }
}
