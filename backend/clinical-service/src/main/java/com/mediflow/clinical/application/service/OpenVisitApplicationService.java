package com.mediflow.clinical.application.service;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.response.OpenVisitDTO;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.common.exception.ForbiddenOperationException;

@Service
@ConditionalOnProperty(prefix = "mediflow.clinical.scheduling", name = "enabled", havingValue = "true")
public class OpenVisitApplicationService {
    private static final int DOCTOR_LIST_LIMIT = 100;

    private final OutpatientRegistrationRepositoryPort registrations;
    private final CurrentClinicalActorPort actors;

    public OpenVisitApplicationService(OutpatientRegistrationRepositoryPort registrations, CurrentClinicalActorPort actors) {
        this.registrations = registrations;
        this.actors = actors;
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
