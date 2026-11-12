package com.mediflow.clinical.application.service;
import com.mediflow.clinical.application.port.out.*;
import com.mediflow.clinical.application.mapper.ClinicalDtoMapper;
import com.mediflow.clinical.application.dto.response.MedicalRecordDTO;
import com.mediflow.clinical.application.dto.response.PatientExamResultDTO;
import com.mediflow.clinical.application.port.out.RecordAmendmentRepositoryPort;
import com.mediflow.common.api.*;
import com.mediflow.common.exception.ForbiddenOperationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PatientResultsService {
    private final MedicalRecordRepositoryPort records;
    private final CurrentClinicalActorPort actors;
    private final ClinicalDtoMapper mapper;
    private final RecordAmendmentRepositoryPort amendments;

    @org.springframework.beans.factory.annotation.Autowired
    public PatientResultsService(MedicalRecordRepositoryPort records, CurrentClinicalActorPort actors,
            ClinicalDtoMapper mapper, RecordAmendmentRepositoryPort amendments) {
        this.records = records; this.actors = actors; this.mapper = mapper; this.amendments = amendments;
    }

    public PatientResultsService(MedicalRecordRepositoryPort records, CurrentClinicalActorPort actors,
            ClinicalDtoMapper mapper) {
        this(records, actors, mapper, null);
    }
    public PageResult<PatientExamResultDTO> mine(PageQuery page) {
        var actor = actors.current();
        if (actor == null || actor.accountId() == null || !"PATIENT".equals(actor.role()) || actor.patientId() == null) {
            throw new ForbiddenOperationException("PATIENT_IDENTITY_REQUIRED", "A verified patient account is required");
        }
        return records.findCompletedByPatient(actor.patientId(), page).map(record -> {
            var dto = mapper.toDto(record);
            if (amendments != null) {
                dto = MedicalRecordDTO.withEffectiveCorrections(dto, EffectiveRecordCorrections.latestPerSection(
                        amendments.findByRecord(record.getRecordId())));
            }
            return PatientExamResultDTO.from(dto);
        });
    }
}
