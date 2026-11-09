package com.mediflow.clinical.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.authority.RecordReadDecision;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.RecordAccessDeclarationRequest;
import com.mediflow.clinical.application.dto.response.RecordAccessDeclarationDTO;
import com.mediflow.clinical.application.dto.response.RecordCapabilitiesDTO;
import com.mediflow.clinical.application.exception.InvalidRecordAccessDeclarationException;
import com.mediflow.clinical.application.port.out.AppointmentRepositoryPort;
import com.mediflow.clinical.application.port.out.CorrelationIdProvider;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.port.out.RecordAccessDeclarationPort;
import com.mediflow.clinical.application.port.out.RecordAccessLogPort;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import com.mediflow.clinical.domain.exception.MedicalRecordNotFoundException;
import com.mediflow.clinical.domain.model.AppointmentStatus;
import com.mediflow.clinical.domain.model.MedicalRecord;
import com.mediflow.clinical.domain.model.MedicalRecordStatus;
import com.mediflow.clinical.domain.model.RecordAccessDeclaration;
import com.mediflow.clinical.domain.model.RecordAccessLogEntry;
import com.mediflow.common.exception.ForbiddenOperationException;

/** Purpose declarations and the per-record capabilities that clients use to shape their screens. */
@Service
public class RecordAccessApplicationService {
    private static final Map<String, Set<String>> PURPOSES_BY_ROLE = Map.of(
            "DOCTOR", Set.of("CONSULTATION", "CARE_CONTINUITY", "QUALITY_REVIEW", "EMERGENCY"),
            "ADMIN", Set.of("RECORD_MANAGEMENT", "QUALITY_REVIEW"),
            "NURSE", Set.of("EMERGENCY"));
    private static final Set<String> PURPOSES_REQUIRING_REASON = Set.of("QUALITY_REVIEW", "RECORD_MANAGEMENT");
    private static final Duration DOCTOR_VALIDITY = Duration.ofHours(96);
    private static final Duration OTHER_VALIDITY = Duration.ofMinutes(60);
    private static final int MIN_REASON_LENGTH = 10;
    private static final int MAX_REASON_LENGTH = 500;

    private final MedicalRecordRepositoryPort records;
    private final AppointmentRepositoryPort appointments;
    private final RecordAccessDeclarationPort declarations;
    private final RecordAccessLogPort log;
    private final RecordReadPolicy readPolicy;
    private final CurrentClinicalActorPort actors;
    private final CorrelationIdProvider correlationIds;
    private final boolean outpatientMvpEnabled;
    private final boolean policyEnabled;

    public RecordAccessApplicationService(MedicalRecordRepositoryPort records, AppointmentRepositoryPort appointments,
            RecordAccessDeclarationPort declarations, RecordAccessLogPort log, RecordReadPolicy readPolicy,
            CurrentClinicalActorPort actors, CorrelationIdProvider correlationIds,
            OutpatientMvpMode outpatientMvpMode, RecordAccessPolicyMode recordAccessPolicyMode) {
        this.records = records;
        this.appointments = appointments;
        this.declarations = declarations;
        this.log = log;
        this.readPolicy = readPolicy;
        this.actors = actors;
        this.correlationIds = correlationIds;
        this.outpatientMvpEnabled = outpatientMvpMode.enabled();
        this.policyEnabled = recordAccessPolicyMode.enabled();
    }

    @Transactional
    public RecordAccessDeclarationDTO declare(RecordAccessDeclarationRequest request) {
        ActorIdentity actor = currentActor();
        if (request.purpose() == null || request.purpose().isBlank()) {
            throw new InvalidRecordAccessDeclarationException("A purpose is required");
        }
        String reason = normalizedReason(request.purpose(), request.reason());
        if (!PURPOSES_BY_ROLE.getOrDefault(actor.role(), Set.of()).contains(request.purpose())) {
            throw new InvalidRecordAccessDeclarationException("This purpose is not available for the current role");
        }
        Instant now = Instant.now();
        Duration validity = "DOCTOR".equals(actor.role()) ? DOCTOR_VALIDITY : OTHER_VALIDITY;
        RecordAccessDeclaration declaration = new RecordAccessDeclaration(UUID.randomUUID(), actor.accountId(),
                actor.staffId(), actor.role(), request.patientId(), request.purpose(), reason, now, now.plus(validity));
        declarations.save(declaration);
        log.append(new RecordAccessLogEntry(actor.accountId(), actor.staffId(), actor.role(), request.patientId(), null,
                "DECLARE", "ALLOWED", null, request.purpose(), correlationIds.currentOrCreate().toString(), now));
        return new RecordAccessDeclarationDTO(declaration.declarationId(), declaration.patientId(),
                declaration.purpose(), declaration.expiresAt());
    }

    public RecordCapabilitiesDTO capabilities(UUID recordId) {
        ActorIdentity actor = currentActor();
        MedicalRecord record = records.findById(recordId)
                .orElseThrow(() -> new MedicalRecordNotFoundException("Medical record not found: " + recordId));
        RecordReadDecision read = policyEnabled
                ? readPolicy.forRecord(actor, record, Instant.now(), correlationIds.currentOrCreate().toString())
                : RecordReadDecision.allow("LEGACY_ROLE");
        boolean inExam = record.getAppointmentId() != null && appointments.findById(record.getAppointmentId())
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.IN_EXAM)
                .isPresent();
        String writeBlocked = writeBlockedReason(actor, record, inExam);
        boolean canWrite = writeBlocked == null;
        return new RecordCapabilitiesDTO(recordId, record.getPatientId(), read.allowed(), read.basis(), canWrite, canWrite && inExam, false,
                writeBlocked);
    }

    private String writeBlockedReason(ActorIdentity actor, MedicalRecord record, boolean inExam) {
        if (record.getStatus() == MedicalRecordStatus.COMPLETED) {
            return "RECORD_COMPLETED";
        }
        boolean writerRole = "DOCTOR".equals(actor.role()) || "ADMIN".equals(actor.role());
        if (!outpatientMvpEnabled && !policyEnabled) {
            return writerRole ? null : "NOT_ASSIGNED_DOCTOR";
        }
        if (!writerRole || actor.staffId() == null || !actor.staffId().equals(record.getDoctorId())) {
            return "NOT_ASSIGNED_DOCTOR";
        }
        return outpatientMvpEnabled && !inExam ? "NOT_IN_EXAM" : null;
    }

    private ActorIdentity currentActor() {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.accountId() == null || actor.role() == null) {
            throw new ForbiddenOperationException(RecordReadPolicy.DENIED, "An authenticated staff identity is required");
        }
        return actor;
    }

    private static String normalizedReason(String purpose, String reason) {
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new InvalidRecordAccessDeclarationException("The reason is too long");
        }
        if (PURPOSES_REQUIRING_REASON.contains(purpose) && trimmed.length() < MIN_REASON_LENGTH) {
            throw new InvalidRecordAccessDeclarationException("This purpose requires a reason of at least 10 characters");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
