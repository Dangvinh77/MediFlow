package com.mediflow.clinical.application.service;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.request.AddAllergyRequest;
import com.mediflow.clinical.application.dto.request.AllergyStatusRequest;
import com.mediflow.clinical.application.dto.response.PatientAllergiesDTO;
import com.mediflow.clinical.application.dto.response.PatientAllergyDTO;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.IdempotentCommandPort;
import com.mediflow.clinical.application.port.out.MedicalRecordRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientAllergyRepositoryPort;
import com.mediflow.clinical.application.port.out.RecordAccessPolicyMode;
import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.clinical.domain.model.AllergySeverity;
import com.mediflow.clinical.domain.model.AllergyStatus;
import com.mediflow.clinical.domain.model.PatientAllergy;
import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.common.exception.ResourceNotFoundException;

/** Patient-level allergy list kept by Clinical; Core Patient keeps administrative data only. */
@Service
public class PatientAllergyApplicationService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Comparator<PatientAllergy> NEWEST_FIRST =
            Comparator.comparing(PatientAllergy::getRecordedAt).reversed().thenComparing(PatientAllergy::getAllergyId);

    private final PatientAllergyRepositoryPort allergies;
    private final MedicalRecordRepositoryPort records;
    private final CurrentClinicalActorPort actors;
    private final RecordReadGuard readGuard;
    private final boolean recordAccessPolicyEnabled;
    private final IdempotentCommandPort idempotency;
    private final Clock clock;

    @Autowired
    public PatientAllergyApplicationService(PatientAllergyRepositoryPort allergies, MedicalRecordRepositoryPort records,
            CurrentClinicalActorPort actors, RecordReadGuard readGuard, RecordAccessPolicyMode mode,
            IdempotentCommandPort idempotency) {
        this(allergies, records, actors, readGuard, mode.enabled(), idempotency, Clock.system(CLINIC_ZONE));
    }

    public PatientAllergyApplicationService(PatientAllergyRepositoryPort allergies, MedicalRecordRepositoryPort records,
            CurrentClinicalActorPort actors, RecordReadGuard readGuard, boolean recordAccessPolicyEnabled,
            IdempotentCommandPort idempotency, Clock clock) {
        this.allergies = allergies;
        this.records = records;
        this.actors = actors;
        this.readGuard = readGuard;
        this.recordAccessPolicyEnabled = recordAccessPolicyEnabled;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PatientAllergiesDTO list(UUID patientId) {
        requireRead(patientId);
        List<PatientAllergy> all = allergies.findByPatient(patientId);
        return new PatientAllergiesDTO(
                all.stream().filter(PatientAllergy::isActive).sorted(NEWEST_FIRST).map(PatientAllergyApplicationService::dto).toList(),
                all.stream().filter(a -> !a.isActive()).sorted(NEWEST_FIRST).map(PatientAllergyApplicationService::dto).toList());
    }

    public IdempotentCommandPort.Result<PatientAllergyDTO> add(UUID patientId, AddAllergyRequest request, String key) {
        ActorIdentity actor = staff("DOCTOR", "NURSE");
        TreatmentCourseApplicationService.requireKey(key);
        String path = "/api/v2/patients/" + patientId + "/allergies";
        return idempotency.execute(actor.accountId().toString(), "POST", path, key,
                TreatmentCourseApplicationService.fingerprint(path + ":" + request), PatientAllergyDTO.class, 201,
                ignored -> null, () -> addNow(actor, patientId, request));
    }

    public IdempotentCommandPort.Result<PatientAllergyDTO> changeStatus(UUID patientId, UUID allergyId,
            AllergyStatusRequest request, String key) {
        ActorIdentity actor = staff("DOCTOR");
        TreatmentCourseApplicationService.requireKey(key);
        String path = "/api/v2/patients/" + patientId + "/allergies/" + allergyId + "/status";
        return idempotency.execute(actor.accountId().toString(), "PUT", path, key,
                TreatmentCourseApplicationService.fingerprint(path + ":" + request), PatientAllergyDTO.class, 200,
                ignored -> null, () -> changeNow(actor, patientId, allergyId, request));
    }

    private PatientAllergyDTO addNow(ActorIdentity actor, UUID patientId, AddAllergyRequest request) {
        requireRead(patientId);
        if (request == null) {
            throw invalid("An allergy is required");
        }
        UUID source = request.sourceRecordId();
        if (source != null && !records.findById(source).map(r -> r.getPatientId().equals(patientId)).orElse(false)) {
            throw invalid("The source visit belongs to another patient or does not exist");
        }
        PatientAllergy allergy = PatientAllergy.record(patientId, request.substance(), request.reaction(),
                severity(request.severity()), source, actor.staffId(), clock.instant());
        if (allergies.findActiveByKey(patientId, allergy.substanceKey()).isPresent()) {
            throw invalid("This substance is already on the active allergy list");
        }
        try {
            allergies.insert(allergy);
        } catch (DuplicateKeyException race) {
            throw invalid("This substance is already on the active allergy list");
        }
        return dto(allergy);
    }

    private PatientAllergyDTO changeNow(ActorIdentity actor, UUID patientId, UUID allergyId, AllergyStatusRequest request) {
        requireRead(patientId);
        PatientAllergy allergy = allergies.findByIdForUpdate(allergyId)
                .filter(found -> found.getPatientId().equals(patientId))
                .orElseThrow(() -> new ResourceNotFoundException("ALLERGY_NOT_FOUND", "Allergy not found: " + allergyId));
        AllergyStatus next;
        try {
            next = AllergyStatus.valueOf(request == null || request.status() == null ? "" : request.status());
        } catch (IllegalArgumentException unknown) {
            throw invalid("status must be RESOLVED or ENTERED_IN_ERROR");
        }
        allergy.changeStatus(next, request.reason(), actor.staffId(), clock.instant());
        allergies.changeStatus(allergy);
        return dto(allergy);
    }

    private void requireRead(UUID patientId) {
        if (recordAccessPolicyEnabled) {
            readGuard.requirePatient(patientId);
        }
    }

    private static AllergySeverity severity(String value) {
        if (value == null || value.isBlank()) {
            return AllergySeverity.UNKNOWN;
        }
        try {
            return AllergySeverity.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            throw invalid("severity must be MILD, MODERATE, SEVERE or UNKNOWN");
        }
    }

    private ActorIdentity staff(String... roles) {
        ActorIdentity actor = actors.current();
        if (actor == null || actor.accountId() == null || actor.staffId() == null
                || !List.of(roles).contains(actor.role())) {
            throw new ForbiddenOperationException("CLINICAL_STAFF_IDENTITY_REQUIRED", "A care staff identity is required");
        }
        return actor;
    }

    private static InvalidClinicalDataException invalid(String message) {
        return new InvalidClinicalDataException("ALLERGY_INVALID", message);
    }

    static PatientAllergyDTO dto(PatientAllergy a) {
        return new PatientAllergyDTO(a.getAllergyId(), a.getSubstance(), a.getReaction(), a.getSeverity().name(),
                a.getStatus().name(), a.getRecordedByStaffId(), a.getRecordedAt(), a.getSourceRecordId(), a.getStatusReason(),
                a.getStatusChangedByStaffId(), a.getStatusChangedAt());
    }
}
