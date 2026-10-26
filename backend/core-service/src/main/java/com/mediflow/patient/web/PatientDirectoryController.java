package com.mediflow.patient.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.dto.response.PatientDirectoryDTO;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.application.port.out.CorrelationIdProvider;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients/directory")
public class PatientDirectoryController {
    private final GetPatientUseCase patients;
    private final CorrelationIdProvider correlationIds;

    public PatientDirectoryController(GetPatientUseCase patients, CorrelationIdProvider correlationIds) {
        this.patients = patients;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PHARMACIST','CASHIER','LAB_TECH')")
    public ResponseEntity<ApiResponse<PageResult<PatientDirectoryDTO>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var data = patients.search(keyword, PageQuery.of(page, size)).map(PatientDirectoryDTO::from);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(data, correlationIds.currentOrCreate().toString()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','PHARMACIST','CASHIER','LAB_TECH')")
    public ResponseEntity<ApiResponse<PatientDirectoryDTO>> getById(@PathVariable UUID id) {
        var data = PatientDirectoryDTO.from(patients.getById(id));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(data, correlationIds.currentOrCreate().toString()));
    }
}
