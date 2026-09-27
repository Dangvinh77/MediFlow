package com.mediflow.inpatient.application.port.in;

import com.mediflow.inpatient.application.dto.request.CloseAdmissionRequest;
import com.mediflow.inpatient.application.dto.request.MedicalDischargeRequest;
import com.mediflow.inpatient.application.dto.response.AdmissionDTO;

import java.util.UUID;

public interface ManageDischargeUseCase {
    AdmissionDTO approveMedicalDischarge(UUID admissionId, MedicalDischargeRequest request,
                                         String correlationId);
    AdmissionDTO close(UUID admissionId, CloseAdmissionRequest request, String correlationId);
}
