package com.mediflow.clinical.application.port.in;

import java.util.UUID;
import java.util.List;
import com.mediflow.clinical.application.dto.request.CreateRecordRequest;
import com.mediflow.clinical.application.dto.request.UpdateRecordRequest;
import com.mediflow.clinical.application.dto.request.AddDiagnosisRequest;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.dto.response.DiagnosisDTO;

public interface ManageRecordUseCase {
    /** BR-R4: record creation and appointment arrival share one local transaction. */
    MedicalRecordDTO create(CreateRecordRequest request);
    MedicalRecordDTO update(UUID id, UpdateRecordRequest request);
    MedicalRecordDTO getById(UUID id);
    List<MedicalRecordDTO> byPatient(UUID patientId);
    DiagnosisDTO addDiagnosis(UUID recordId, AddDiagnosisRequest request);
}
