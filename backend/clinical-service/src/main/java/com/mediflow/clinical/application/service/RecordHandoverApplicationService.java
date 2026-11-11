package com.mediflow.clinical.application.service;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.RequestHandoverRequest;
import com.mediflow.clinical.application.dto.response.RecordHandoverDTO;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import com.mediflow.clinical.application.port.out.RecordHandoverRepositoryPort;
import com.mediflow.clinical.application.port.out.StaffLookupPort;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.exception.MedicalRecordNotFoundException;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordHandover;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;

/**
 * Hands an open visit to another doctor of the same department. The opening doctor stays on the record and on
 * every published event; only write responsibility moves.
 */
@Service
@ConditionalOnProperty(prefix = "mediflow.v2", name = "record-amendment", havingValue = "true")
public class RecordHandoverApplicationService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RecordHandoverRepositoryPort handovers;
    private final MedicalRecordRepositoryPort records;
    private final CurrentClinicalActorPort actors;
    private final StaffLookupPort staff;
    private final ClinicalStaffAuthority staffAuthority;
    private final RecordReadGuard readGuard;
    private final boolean recordAccessPolicyEnabled;
    private final IdempotentCommandPort idempotency;
    private final Clock clock;

    @Autowired
    public RecordHandoverApplicationService(RecordHandoverRepositoryPort handovers, MedicalRecordRepositoryPort records,
            CurrentClinicalActorPort actors, StaffLookupPort staff, RecordReadGuard readGuard, RecordAccessPolicyMode mode,
            IdempotentCommandPort idempotency) {
        this(handovers, records, actors, staff, readGuard, mode.enabled(), idempotency, Clock.system(CLINIC_ZONE));
    }

    public RecordHandoverApplicationService(RecordHandoverRepositoryPort handovers, MedicalRecordRepositoryPort records,
            CurrentClinicalActorPort actors, StaffLookupPort staff, RecordReadGuard readGuard,
            boolean recordAccessPolicyEnabled, IdempotentCommandPort idempotency, Clock clock) {
        this.handovers = handovers;
        this.records = records;
        this.actors = actors;
        this.staff = staff;
        this.staffAuthority = new ClinicalStaffAuthority(actors, staff);
        this.readGuard = readGuard;
        this.recordAccessPolicyEnabled = recordAccessPolicyEnabled;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public IdempotentCommandPort.Result<RecordHandoverDTO> request(UUID recordId, RequestHandoverRequest request,
            String key) {
        ActorIdentity actor = doctor();
        TreatmentCourseApplicationService.requireKey(key);
        String path = "/api/v2/records/" + recordId + "/handovers";
        return idempotency.execute(actor.accountId().toString(), "POST", path, key,
                TreatmentCourseApplicationService.fingerprint(path + ":" + request), RecordHandoverDTO.class, 201,
                dto -> dto == null ? null : path + "/" + dto.handoverId(), () -> requestNow(actor, recordId, request));
    }

    public IdempotentCommandPort.Result<RecordHandoverDTO> accept(UUID recordId, UUID handoverId, String key) {
        ActorIdentity actor = doctor();
        TreatmentCourseApplicationService.requireKey(key);
        String path = "/api/v2/records/" + recordId + "/handovers/" + handoverId + "/acceptance";
        return idempotency.execute(actor.accountId().toString(), "PUT", path, key,
                TreatmentCourseApplicationService.fingerprint(path), RecordHandoverDTO.class, 200, ignored -> null,
                () -> acceptNow(actor, recordId, handoverId));
    }

    @Transactional(readOnly = true)
    public List<RecordHandoverDTO> list(UUID recordId) {
        MedicalRecord record = records.findById(recordId).orElseThrow(() -> notFound(recordId));
        if (recordAccessPolicyEnabled) {
            readGuard.requireRecord(record);
        }
        return handovers.findByRecord(recordId).stream().map(RecordHandoverApplicationService::dto).toList();
    }

    private RecordHandoverDTO requestNow(ActorIdentity actor, UUID recordId, RequestHandoverRequest request) {
        MedicalRecord record = openRecord(recordId);
        staffAuthority.requireAssignedDoctor(record.responsibleDoctorId(), record.getDepartmentId());
        UUID receiver = request == null ? null : request.toDoctorId();
        requireColleague(receiver, record);
        handovers.findPending(recordId).ifPresent(pending -> {
            pending.supersede();
            handovers.supersede(pending);
        });
        RecordHandover handover = RecordHandover.request(recordId, actor.staffId(), receiver,
                request.reason(), request.pendingItems(), clock.instant());
        try {
            handovers.insert(handover);
        } catch (DuplicateKeyException race) {
            throw pendingExists();
        }
        return dto(handover);
    }

    private RecordHandoverDTO acceptNow(ActorIdentity actor, UUID recordId, UUID handoverId) {
        MedicalRecord record = openRecord(recordId);
        RecordHandover handover = handovers.findByIdForUpdate(handoverId)
                .filter(found -> found.getRecordId().equals(recordId))
                .orElseThrow(() -> new ResourceNotFoundException("HANDOVER_NOT_FOUND", "Handover not found: " + handoverId));
        var at = clock.instant();
        handover.accept(actor.staffId(), at);
        if (!handover.getFromDoctorId().equals(record.responsibleDoctorId())) {
            throw new InvalidClinicalDataException("HANDOVER_INVALID", "The visit has changed hands since the request");
        }
        requireColleague(handover.getToDoctorId(), record);
        record.handOverTo(handover.getToDoctorId(), at);
        records.save(record);
        handovers.accept(handover);
        return dto(handover);
    }

    private MedicalRecord openRecord(UUID recordId) {
        MedicalRecord record = records.findByIdForUpdate(recordId).orElseThrow(() -> notFound(recordId));
        if (record.getStatus() != MedicalRecordStatus.OPEN) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Only an open visit can be handed over");
        }
        return record;
    }

    private void requireColleague(UUID receiver, MedicalRecord record) {
        if (receiver == null || !staff.departmentOf(receiver).map(record.getDepartmentId()::equals).orElse(false)) {
            throw new InvalidClinicalDataException("HANDOVER_INVALID",
                    "The receiving doctor must be an active doctor of the visit department");
        }
    }

    private ActorIdentity doctor() {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.accountId() == null || actor.staffId() == null || !"DOCTOR".equals(actor.role())) {
            throw new ForbiddenOperationException("CLINICAL_STAFF_IDENTITY_REQUIRED", "A doctor identity is required");
        }
        return actor;
    }

    private static InvalidClinicalDataException pendingExists() {
        return new InvalidClinicalDataException("HANDOVER_PENDING_EXISTS", "This visit already has a pending handover");
    }

    private static MedicalRecordNotFoundException notFound(UUID recordId) {
        return new MedicalRecordNotFoundException("Medical record not found: " + recordId);
    }

    static RecordHandoverDTO dto(RecordHandover handover) {
        return new RecordHandoverDTO(handover.getHandoverId(), handover.getRecordId(), handover.getFromDoctorId(),
                handover.getToDoctorId(), handover.getReason(), handover.getPendingItems(), handover.getStatus().name(),
                handover.getRequestedAt(), handover.getAcceptedAt());
    }
}
