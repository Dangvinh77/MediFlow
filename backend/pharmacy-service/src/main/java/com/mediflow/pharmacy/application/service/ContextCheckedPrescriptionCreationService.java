package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.port.in.CreateContextCheckedPrescriptionUseCase;
import com.mediflow.pharmacy.application.port.in.CreateCarePrescriptionWithContextUseCase;
import com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort;
import com.mediflow.pharmacy.application.port.out.PrescriptionIdentityPort;
import java.time.Clock;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Current identity and Clinical relationships are required; neither substitutes for an order/grant. */
public class ContextCheckedPrescriptionCreationService implements CreateContextCheckedPrescriptionUseCase {
    private final OutpatientPrescriptionContextPort contexts;
    private final CreateCarePrescriptionWithContextUseCase writer;
    private final Clock clock;
    private final PrescriptionIdentityPort identities;
    public ContextCheckedPrescriptionCreationService(OutpatientPrescriptionContextPort contexts,
            CreateCarePrescriptionWithContextUseCase writer, Clock clock, PrescriptionIdentityPort identities) {
        this.contexts = contexts; this.writer = writer; this.clock = clock;
        this.identities = java.util.Objects.requireNonNull(identities);
    }
    @Override @Transactional(propagation = Propagation.NEVER)
    public UUID createWithContext(UUID commandId, CreatePrescriptionCommand command) {
        CarePrescriptionCreationService.validate(commandId, command);
        var request = command.request();
        if (request.recordId() == null) throw new com.mediflow.pharmacy.domain.exception.PrescriptionRuleException(
                "PHARMACY_CARE_CONTEXT_INVALID", "Exact outpatient record is required");
        var observation = contexts.findRecord(request.recordId(), command.correlationId());
        observation.requireExact(request.recordId(), request.patientId(), request.doctorId(), request.departmentId(),
                request.careEpisodeId(), clock.instant());
        var identity = identities.lookup(request.patientId(), request.doctorId(), request.departmentId(), command.correlationId());
        if (identity == null) throw new com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException();
        identity.requireExact(request.patientId(), request.doctorId(), request.departmentId(), clock.instant());
        observation.requireExact(request.recordId(), request.patientId(), request.doctorId(), request.departmentId(),
                request.careEpisodeId(), clock.instant());
        return writer.createCare(commandId, command, observation, identity);
    }
}
