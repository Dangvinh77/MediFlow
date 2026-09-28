package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryCaseAuditEntry;
import com.mediflow.surgery.domain.model.SurgeryStateChange;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class SurgeryCasePersistenceMapper {

    SurgeryCase toDomain(SurgeryCaseJpaEntity row, ReadinessSnapshot readiness,
                         List<SurgeryStateChange> states, List<SurgeryCaseAuditEntry> audit) {
        SurgeryAuditActor cancelledBy = row.cancellationActorType == null ? null
                : new SurgeryAuditActor(row.cancellationActorType, row.cancellationAccountId,
                row.cancellationStaffId, row.cancellationSystemProducer);
        return SurgeryCase.restore(row.surgeryCaseId, row.surgeryRequestId,
                new CareEpisode(row.episodeType, row.episodeId, row.admissionId, row.medicalRecordId),
                row.patientId, row.departmentId, row.requestedBy, row.procedureCode, row.indication,
                row.priority, row.requestedAt, row.status, readiness,
                row.readyAt, row.startedAt, row.completedAt,
                row.cancelledAt, cancelledBy, row.cancellationReason, row.revision, states, audit);
    }

    SurgeryCaseJpaEntity toRow(SurgeryCase aggregate, SurgeryCaseJpaEntity row, Instant now) {
        row.surgeryCaseId = aggregate.getSurgeryCaseId();
        row.surgeryRequestId = aggregate.getSurgeryRequestId();
        row.episodeType = aggregate.getCareEpisode().episodeType();
        row.episodeId = aggregate.getCareEpisode().episodeId();
        row.admissionId = aggregate.getCareEpisode().admissionId();
        row.medicalRecordId = aggregate.getCareEpisode().medicalRecordId();
        row.patientId = aggregate.getPatientId();
        row.departmentId = aggregate.getDepartmentId();
        row.requestedBy = aggregate.getRequestedBy();
        row.procedureCode = aggregate.getProcedureCode();
        row.indication = aggregate.getIndication();
        row.priority = aggregate.getPriority();
        row.status = aggregate.getStatus();
        row.readinessSnapshotId = aggregate.getReadinessSnapshot() == null
                ? null : aggregate.getReadinessSnapshot().readinessSnapshotId();
        row.requestedAt = aggregate.getRequestedAt();
        row.readyAt = aggregate.getReadyAt();
        row.startedAt = aggregate.getStartedAt();
        row.completedAt = aggregate.getCompletedAt();
        row.cancelledAt = aggregate.getCancelledAt();
        SurgeryAuditActor cancellationActor = aggregate.getCancelledBy();
        row.cancellationActorType = cancellationActor == null ? null : cancellationActor.actorType();
        row.cancellationAccountId = cancellationActor == null ? null : cancellationActor.accountId();
        row.cancellationStaffId = cancellationActor == null ? null : cancellationActor.verifiedStaffId();
        row.cancellationSystemProducer = cancellationActor == null ? null : cancellationActor.systemProducer();
        row.cancellationReason = aggregate.getCancellationReason();
        row.revision = aggregate.getRevision();
        if (row.createdAt == null) row.createdAt = now;
        row.updatedAt = now;
        return row;
    }

    SurgeryCaseJpaEntity newRow() {
        return new SurgeryCaseJpaEntity();
    }
}
