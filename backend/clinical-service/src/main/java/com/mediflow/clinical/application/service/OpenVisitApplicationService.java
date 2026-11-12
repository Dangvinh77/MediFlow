package com.mediflow.clinical.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.response.OpenVisitDTO;
import com.mediflow.clinical.application.dto.response.ReturnSourceDTO;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.common.exception.ForbiddenOperationException;

@Service
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class OpenVisitApplicationService {
    private static final int DOCTOR_LIST_LIMIT = 100;

    private final OutpatientRegistrationRepositoryPort registrations;
    private final CurrentClinicalActorPort actors;
    private final MedicalRecordRepositoryPort records;

    public OpenVisitApplicationService(OutpatientRegistrationRepositoryPort registrations, CurrentClinicalActorPort actors,
            MedicalRecordRepositoryPort records) {
        this.registrations = registrations;
        this.actors = actors;
        this.records = records;
    }

    /**
     * Finished visits reception may name as the source of a return visit. Registration needs the link, not the
     * content, so this bypasses the record-read policy and returns only status, date and department.
     */
    public List<ReturnSourceDTO> returnSources(UUID patientId) {
        return records.findByPatient(patientId).stream()
                .filter(record -> record.getStatus() != MedicalRecordStatus.OPEN)
                .sorted(Comparator.comparing(MedicalRecord::getExaminationDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(record -> new ReturnSourceDTO(record.getRecordId(), record.getStatus().name(),
                        record.getExaminationDate(), record.getDepartmentId()))
                .toList();
    }

    /** Reception warns about visits in any department, so this is not narrowed to one. */
    public List<OpenVisitDTO> forPatient(UUID patientId) {
        return registrations.findOpenVisitsByPatient(patientId);
    }

    public List<OpenVisitDTO> forCurrentDoctor() {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.staffId() == null) {
            throw new ForbiddenOperationException("CLINICAL_STAFF_IDENTITY_REQUIRED", "A doctor staff identity is required");
        }
        return registrations.findOpenVisitsByDoctor(actor.staffId(), DOCTOR_LIST_LIMIT);
    }
}
