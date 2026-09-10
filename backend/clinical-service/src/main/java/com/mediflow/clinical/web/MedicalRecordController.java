package com.mediflow.clinical.web;

import com.mediflow.clinical.application.dto.request.AddDiagnosisRequest;
import com.mediflow.clinical.application.dto.request.CreateRecordRequest;
import com.mediflow.clinical.application.dto.request.UpdateRecordRequest;
import com.mediflow.clinical.application.dto.response.DiagnosisDTO;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.port.in.ManageRecordUseCase;
import com.mediflow.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** HTTP adapter for medical records and their diagnoses. */
@RestController
@RequestMapping("/api/v1/records")
@RequiredArgsConstructor
public class MedicalRecordController {

    private static final String BASE_PATH = "/api/v1/records";

    private final ManageRecordUseCase manageRecordUseCase;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<MedicalRecordDTO>> getById(
            @PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.ok(manageRecordUseCase.getById(id)));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<List<MedicalRecordDTO>>> getByPatient(
            @PathVariable UUID patientId) {

        return ResponseEntity.ok(ApiResponse.ok(
                manageRecordUseCase.byPatient(patientId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<MedicalRecordDTO>> create(
            @Valid @RequestBody CreateRecordRequest request) {

        MedicalRecordDTO created = manageRecordUseCase.create(request);
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + created.recordId()))
                .body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<MedicalRecordDTO>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRecordRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(manageRecordUseCase.update(id, request)));
    }

    @PostMapping("/{id}/diagnoses")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public ResponseEntity<ApiResponse<DiagnosisDTO>> addDiagnosis(
            @PathVariable UUID id,
            @Valid @RequestBody AddDiagnosisRequest request) {

        DiagnosisDTO created = manageRecordUseCase.addDiagnosis(id, request);
        URI location = URI.create(
                BASE_PATH + "/" + id + "/diagnoses/" + created.diagnosisId());
        return ResponseEntity.created(location).body(ApiResponse.ok(created));
    }
}
