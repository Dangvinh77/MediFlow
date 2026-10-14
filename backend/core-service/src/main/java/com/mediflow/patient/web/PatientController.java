package com.mediflow.patient.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.dto.response.PatientLookupDTO;
import com.mediflow.patient.application.dto.request.CreatePatientRequest;
import com.mediflow.patient.application.dto.request.UpdatePatientRequest;
import com.mediflow.patient.application.port.in.CreatePatientUseCase;
import com.mediflow.patient.application.port.in.DeletePatientUseCase;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.application.port.in.ReadPatientIdentityUseCase;
import com.mediflow.patient.application.port.in.UpdatePatientUseCase;
import jakarta.validation.Valid;
import com.mediflow.patient.application.port.out.CorrelationIdProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {
    private final GetPatientUseCase patients;
    private final ReadPatientIdentityUseCase lookup;
    private final CreatePatientUseCase createPatient;
    private final UpdatePatientUseCase updatePatient;
    private final DeletePatientUseCase deletePatient;
    private final CorrelationIdProvider correlationIds;

    public PatientController(GetPatientUseCase patients, ReadPatientIdentityUseCase lookup,
                             CreatePatientUseCase createPatient, UpdatePatientUseCase updatePatient,
                             DeletePatientUseCase deletePatient, CorrelationIdProvider correlationIds) {
        this.patients = patients;
        this.lookup = lookup;
        this.createPatient = createPatient;
        this.updatePatient = updatePatient;
        this.deletePatient = deletePatient;
        this.correlationIds = correlationIds;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<PatientDTO>> create(@Valid @RequestBody CreatePatientRequest request) {
        PatientDTO created = createPatient.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/patients/" + created.maBenhNhan()))
                .body(ApiResponse.ok(created, correlationId()));
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<PatientDTO>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdatePatientRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(updatePatient.update(id, request), correlationId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deletePatient.delete(id);
        return ResponseEntity.noContent().build();
    }

    private String correlationId() { return correlationIds.currentOrCreate().toString(); }
}
