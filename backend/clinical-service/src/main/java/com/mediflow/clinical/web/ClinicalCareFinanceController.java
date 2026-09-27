package com.mediflow.clinical.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.CompleteRecordRequest;
import com.mediflow.clinical.application.dto.request.CreateAdmissionReferralRequest;
import com.mediflow.clinical.application.dto.request.StartExamRequest;
import com.mediflow.clinical.application.dto.response.AdmissionReferralDTO;
import com.mediflow.clinical.application.dto.response.AppointmentDTO;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.port.in.CompleteMedicalRecordUseCase;
import com.mediflow.clinical.application.port.in.ManageExamGateUseCase;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@ConditionalOnProperty(prefix = "mediflow.features.care-finance-v2", name = "enabled", havingValue = "true")
public class ClinicalCareFinanceController {
    private final ManageExamGateUseCase examGate;
    private final CompleteMedicalRecordUseCase records;
    private final CorrelationIdProvider correlationIds;

    public ClinicalCareFinanceController(ManageExamGateUseCase examGate,
                                         CompleteMedicalRecordUseCase records,
                                         CorrelationIdProvider correlationIds) {
        this.examGate = examGate;
        this.records = records;
        this.correlationIds = correlationIds;
    }

    @PutMapping("/api/v1/appointments/{id}/check-in")
    @PreAuthorize("hasAnyRole('ADMIN','NURSE')")
    public ResponseEntity<ApiResponse<AppointmentDTO>> checkIn(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(examGate.checkIn(id), correlationIds.currentOrCreate().toString()));
    }

    @PutMapping("/api/v1/appointments/{id}/start-exam")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<ApiResponse<AppointmentDTO>> startExam(
            @PathVariable UUID id, @Valid @RequestBody StartExamRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                examGate.startExam(id, request), correlationIds.currentOrCreate().toString()));
    }

    @PutMapping("/api/v1/records/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<ApiResponse<MedicalRecordDTO>> complete(
            @PathVariable UUID id, @Valid @RequestBody CompleteRecordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                records.complete(id, request), correlationIds.currentOrCreate().toString()));
    }

    @PostMapping("/api/v1/records/{id}/admission-referrals")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public ResponseEntity<ApiResponse<AdmissionReferralDTO>> requestAdmission(
            @PathVariable UUID id, @Valid @RequestBody CreateAdmissionReferralRequest request) {
        AdmissionReferralDTO result = records.requestAdmission(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/records/" + id + "/admission-referrals/"
                        + result.admissionRequestId()))
                .body(ApiResponse.ok(result, correlationIds.currentOrCreate().toString()));
    }
}
