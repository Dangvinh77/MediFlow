package com.mediflow.patient.infrastructure.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.application.dto.request.CreatePatientRequest;
import com.mediflow.patient.application.dto.request.UpdatePatientRequest;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.port.in.CreatePatientUseCase;
import com.mediflow.patient.application.port.in.DeletePatientUseCase;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.application.port.in.UpdatePatientUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

import java.util.UUID;

/**
 * Driving adapter for HTTP. Roles come straight from docs/ai/services/patient.md — every endpoint
 * declares them, default deny.
 *
 * <p>Depends on the in-ports, not on the application service class: the controller states what it
 * needs, not who provides it.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final CreatePatientUseCase createPatient;
    private final UpdatePatientUseCase updatePatient;
    private final DeletePatientUseCase deletePatient;
    private final GetPatientUseCase getPatient;

    public PatientController(CreatePatientUseCase createPatient,
                             UpdatePatientUseCase updatePatient,
                             DeletePatientUseCase deletePatient,
                             GetPatientUseCase getPatient) {
        this.createPatient = createPatient;
        this.updatePatient = updatePatient;
        this.deletePatient = deletePatient;
        this.getPatient = getPatient;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ApiResponse<PatientDTO> getById(@PathVariable UUID id) {
        return ApiResponse.ok(getPatient.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ApiResponse<PageResult<PatientDTO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(getPatient.search(keyword, PageQuery.of(page, size)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<PatientDTO>> create(@Valid @RequestBody CreatePatientRequest request) {
        PatientDTO created = createPatient.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ApiResponse<PatientDTO> update(@PathVariable UUID id,
                                          @Valid @RequestBody UpdatePatientRequest request) {
        return ApiResponse.ok(updatePatient.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deletePatient.delete(id);
        return ResponseEntity.noContent().build();
    }
}
