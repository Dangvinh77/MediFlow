package com.mediflow.clinical.application.service;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.RecordAmendmentRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordHandoverRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordReviewAuthorityPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordHandover;

/**
 * Screen hints for handover, consultation and amendment. Hiding a button is UX only: every command re-checks the
 * same rules, so an authority outage here only hides the decision action.
 */
@Component
@ConditionalOnProperty(prefix = "mediflow.v2", name = "record-amendment", havingValue = "true")
public class RecordCollaborationCapabilities {
    public record Flags(boolean canHandover, boolean canAcceptHandover, boolean canAddConsultationNote, boolean canAmend,
            boolean canDecideAmendments) {}

    private final RecordHandoverRepositoryPort handovers;
    private final RecordAmendmentRepositoryPort amendments;
    private final StaffLookupPort staff;
    private final SchedulingAuthorityPort authority;
    private final RecordReviewAuthorityPort review;
    private final CorrelationIdProvider correlationIds;

    public RecordCollaborationCapabilities(RecordHandoverRepositoryPort handovers, RecordAmendmentRepositoryPort amendments,
            StaffLookupPort staff, SchedulingAuthorityPort authority, RecordReviewAuthorityPort review,
            CorrelationIdProvider correlationIds) {
        this.handovers = handovers;
        this.amendments = amendments;
        this.staff = staff;
        this.authority = authority;
        this.review = review;
        this.correlationIds = correlationIds;
    }

    public Flags resolve(ActorIdentity actor, MedicalRecord record, boolean canRead) {
        UUID staffId = actor.staffId();
        if (!"DOCTOR".equals(actor.role()) || staffId == null) {
            return new Flags(false, false, false, false, false);
        }
        boolean open = record.getStatus() == MedicalRecordStatus.OPEN;
        boolean inDepartment = staff.departmentOf(staffId).map(record.getDepartmentId()::equals).orElse(false);
        boolean responsible = staffId.equals(record.responsibleDoctorId());
        RecordHandover pending = open ? handovers.findPending(record.getRecordId()).orElse(null) : null;
        boolean canHandover = open && responsible && inDepartment;
        boolean canAccept = pending != null && staffId.equals(pending.getToDoctorId());
        boolean canNote = open && canRead;
        boolean canAmend = !open && inDepartment
                && (responsible || amendments.findAssignment(record.getRecordId(), staffId).isPresent());
        boolean canDecide = !open && canDecide(actor, record.getDepartmentId());
        return new Flags(canHandover, canAccept, canNote, canAmend, canDecide);
    }

    private boolean canDecide(ActorIdentity actor, UUID departmentId) {
        try {
            SchedulingAuthoritySnapshot head = authority.authority(actor.accountId(), departmentId,
                    correlationIds.currentOrCreate().toString());
            if (head != null && Boolean.TRUE.equals(head.canManageSchedules()) && Boolean.TRUE.equals(head.staffActive())
                    && actor.staffId().equals(head.staffId()) && departmentId.equals(head.departmentId())) {
                return true;
            }
        } catch (RuntimeException unavailable) {
            // fall through to the reviewer check; the decision command reports the outage itself
        }
        try {
            return review.canReviewRecords(actor.accountId(), correlationIds.currentOrCreate());
        } catch (RuntimeException unavailable) {
            return false;
        }
    }
}
