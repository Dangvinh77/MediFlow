package com.mediflow.clinical.application.mapper;

import com.mediflow.clinical.application.dto.request.AddDiagnosisRequest;
import com.mediflow.clinical.application.dto.response.AppointmentDTO;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.dto.response.DiagnosisDTO;
import com.mediflow.clinical.domain.model.Appointment;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.Diagnosis;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClinicalDtoMapper {
    AppointmentDTO toDto(Appointment appointment);
    MedicalRecordDTO toDto(MedicalRecord record);
    DiagnosisDTO toDto(Diagnosis diagnosis);

    default Diagnosis toDomain(AddDiagnosisRequest request) {
        return Diagnosis.create(request.diagnosisName(), request.description(), request.icdCode());
    }
}
