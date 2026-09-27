package com.mediflow.clinical.application.port.in;

import java.util.UUID;

import com.mediflow.clinical.application.dto.request.CompleteRecordRequest;
import com.mediflow.clinical.application.dto.request.CreateAdmissionReferralRequest;
import com.mediflow.clinical.application.dto.response.AdmissionReferralDTO;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;

public interface CompleteMedicalRecordUseCase {
    MedicalRecordDTO complete(UUID recordId, CompleteRecordRequest request);
    AdmissionReferralDTO requestAdmission(UUID recordId, CreateAdmissionReferralRequest request);
}
