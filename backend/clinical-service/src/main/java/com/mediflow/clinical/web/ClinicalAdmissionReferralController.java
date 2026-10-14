package com.mediflow.clinical.web;

import java.net.URI;
import java.util.UUID;

import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mediflow.clinical.application.dto.request.CreateAdmissionReferralRequest;
import com.mediflow.clinical.application.dto.response.AdmissionReferralDTO;
import com.mediflow.clinical.application.port.in.CompleteMedicalRecordUseCase;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.common.api.ApiResponse;

import jakarta.validation.Valid;

/** Deferred route that is absent unless both legacy care finance and its own gate are enabled. */
@RestController
@Conditional(AdmissionReferralEnabledCondition.class)
public class ClinicalAdmissionReferralController {

    private final CompleteMedicalRecordUseCase records;
    private final CorrelationIdProvider correlationIds;

    public ClinicalAdmissionReferralController(CompleteMedicalRecordUseCase records,
                                               CorrelationIdProvider correlationIds) {
        this.records = records;
        this.correlationIds = correlationIds;
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
