package com.mediflow.inpatient.application.port.in;

import com.mediflow.inpatient.application.dto.request.CorrectTreatmentEntryRequest;
import com.mediflow.inpatient.application.dto.request.CreateTreatmentEntryRequest;
import com.mediflow.inpatient.application.dto.request.RegisterOrderReferenceRequest;
import com.mediflow.inpatient.application.dto.response.ClinicalOrderReferenceDTO;
import com.mediflow.inpatient.application.dto.response.TreatmentEntryDTO;

import java.util.UUID;

public interface ManageTreatmentUseCase {
    TreatmentEntryDTO append(UUID admissionId, CreateTreatmentEntryRequest request);
    TreatmentEntryDTO correct(UUID admissionId, UUID entryId, CorrectTreatmentEntryRequest request);
    ClinicalOrderReferenceDTO registerOrder(UUID admissionId, RegisterOrderReferenceRequest request);
}
