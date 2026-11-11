package com.mediflow.clinical.application.service;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.ConsultationNoteRequest;
import com.mediflow.clinical.application.dto.response.ConsultationNoteDTO;
import com.mediflow.clinical.application.port.out.ConsultationNoteRepositoryPort;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.exception.MedicalRecordNotFoundException;
import com.mediflow.clinical.domain.model.ConsultationNote;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.common.exception.ForbiddenOperationException;

/** Any doctor who may read an open visit can add an opinion; the responsible doctor still decides the diagnosis. */
@Service
@ConditionalOnProperty(prefix = "mediflow.v2", name = "record-amendment", havingValue = "true")
public class ConsultationNoteApplicationService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ConsultationNoteRepositoryPort notes;
    private final MedicalRecordRepositoryPort records;
    private final CurrentClinicalActorPort actors;
    private final RecordReadGuard readGuard;
    private final boolean recordAccessPolicyEnabled;
    private final IdempotentCommandPort idempotency;
    private final Clock clock;

    @Autowired
    public ConsultationNoteApplicationService(ConsultationNoteRepositoryPort notes, MedicalRecordRepositoryPort records,
            CurrentClinicalActorPort actors, RecordReadGuard readGuard, RecordAccessPolicyMode mode,
            IdempotentCommandPort idempotency) {
        this(notes, records, actors, readGuard, mode.enabled(), idempotency, Clock.system(CLINIC_ZONE));
    }

    public ConsultationNoteApplicationService(ConsultationNoteRepositoryPort notes, MedicalRecordRepositoryPort records,
            CurrentClinicalActorPort actors, RecordReadGuard readGuard, boolean recordAccessPolicyEnabled,
            IdempotentCommandPort idempotency, Clock clock) {
        this.notes = notes;
        this.records = records;
        this.actors = actors;
        this.readGuard = readGuard;
        this.recordAccessPolicyEnabled = recordAccessPolicyEnabled;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    public IdempotentCommandPort.Result<ConsultationNoteDTO> add(UUID recordId, ConsultationNoteRequest request, String key) {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.accountId() == null || actor.staffId() == null || !"DOCTOR".equals(actor.role())) {
            throw new ForbiddenOperationException("CLINICAL_STAFF_IDENTITY_REQUIRED", "A doctor identity is required");
        }
        TreatmentCourseApplicationService.requireKey(key);
        String path = "/api/v2/records/" + recordId + "/consultation-notes";
        return idempotency.execute(actor.accountId().toString(), "POST", path, key,
                TreatmentCourseApplicationService.fingerprint(path + ":" + request), ConsultationNoteDTO.class, 201,
                ignored -> null, () -> addNow(actor, recordId, request));
    }

    @Transactional(readOnly = true)
    public List<ConsultationNoteDTO> list(UUID recordId) {
        MedicalRecord record = records.findById(recordId).orElseThrow(() -> notFound(recordId));
        if (recordAccessPolicyEnabled) {
            readGuard.requireRecord(record);
        }
        return notes.findByRecord(recordId).stream().map(ConsultationNoteApplicationService::dto).toList();
    }

    private ConsultationNoteDTO addNow(ActorIdentity actor, UUID recordId, ConsultationNoteRequest request) {
        MedicalRecord record = records.findByIdForUpdate(recordId).orElseThrow(() -> notFound(recordId));
        if (recordAccessPolicyEnabled) {
            readGuard.requireRecord(record);
        }
        if (record.getStatus() != MedicalRecordStatus.OPEN) {
            throw new InvalidClinicalDataException("CLINICAL_INVALID_STATUS_TRANSITION",
                    "Consultation opinions are added while the visit is open");
        }
        ConsultationNote note = ConsultationNote.write(recordId, actor.staffId(),
                request == null ? null : request.opinion(), clock.instant());
        notes.insert(note);
        return dto(note);
    }

    private static MedicalRecordNotFoundException notFound(UUID recordId) {
        return new MedicalRecordNotFoundException("Medical record not found: " + recordId);
    }

    static ConsultationNoteDTO dto(ConsultationNote note) {
        return new ConsultationNoteDTO(note.getNoteId(), note.getRecordId(), note.getAuthorStaffId(), note.getOpinion(),
                note.getCreatedAt());
    }
}
