package com.mediflow.inpatient.application.port.in;

import com.mediflow.common.api.PageResult;
import com.mediflow.inpatient.application.dto.query.AdmissionSearchQuery;
import com.mediflow.inpatient.application.dto.request.AdmitRequest;
import com.mediflow.inpatient.application.dto.request.CancelAdmissionRequest;
import com.mediflow.inpatient.application.dto.request.CreateAdmissionRequest;
import com.mediflow.inpatient.application.dto.response.AdmissionDTO;

import java.util.UUID;

public interface ManageAdmissionUseCase {
    AdmissionDTO create(CreateAdmissionRequest request, String correlationId);
    AdmissionDTO get(UUID admissionId);
    PageResult<AdmissionDTO> search(AdmissionSearchQuery query);
    AdmissionDTO admit(UUID admissionId, AdmitRequest request, String correlationId);
    AdmissionDTO cancel(UUID admissionId, CancelAdmissionRequest request, String correlationId);
}
