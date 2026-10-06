package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryReadinessEvidence;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.UUID;

/** Seven independent guards; missing/unverifiable/expired evidence never grants permission. */
public final class SurgeryReadinessEngine {
    private final Duration observationAge;
    private final Duration futureSkew;

    public SurgeryReadinessEngine(Duration observationAge, Duration futureSkew) {
        if (observationAge == null || observationAge.isNegative() || observationAge.isZero()
                || futureSkew == null || futureSkew.isNegative()) throw new IllegalArgumentException("Invalid freshness policy");
        this.observationAge = observationAge;
        this.futureSkew = futureSkew;
    }

    public ReadinessSnapshot evaluate(SurgeryCase value, SurgerySchedule schedule,
            SurgeryReadinessEvidence evidence, Instant at) {
        if (value == null || schedule == null || evidence == null || at == null
                || !value.getSurgeryCaseId().equals(evidence.surgeryCaseId())
                || !value.getPatientId().equals(evidence.patientId())
                || !value.getDepartmentId().equals(evidence.departmentId())
                || !value.getCareEpisode().equals(evidence.episode())
                || value.getRevision() != evidence.caseRevision()
                || !value.getSurgeryCaseId().equals(schedule.surgeryCaseId())
                || !schedule.scheduleId().equals(evidence.scheduleId())
                || schedule.revision() != evidence.scheduleRevision()) throw new SurgeryRevisionConflictException();
        var guards = new EnumMap<SurgeryDependencyType, Boolean>(SurgeryDependencyType.class);
        Instant validUntil = null;
        for (var proof : evidence.proofs()) {
            boolean valid = proof.decision() == SurgeryReadinessEvidence.Decision.SATISFIED
                    && !proof.observedAt().isBefore(at.minus(observationAge))
                    && !proof.observedAt().isAfter(at.plus(futureSkew))
                    && !at.isBefore(proof.validFrom())
                    && (proof.validUntil() == null || at.isBefore(proof.validUntil()));
            if (proof.type() == SurgeryDependencyType.SCHEDULE) {
                valid &= schedule.scheduleId().equals(proof.sourceId()) && schedule.revision() == proof.revision();
            }
            guards.put(proof.type(), valid);
            if (valid && proof.validUntil() != null && (validUntil == null || proof.validUntil().isBefore(validUntil))) {
                validUntil = proof.validUntil();
            }
        }
        var dependencies = Arrays.stream(SurgeryDependencyType.values()).flatMap(type -> evidence.proofs().stream()
                .filter(proof -> proof.type() == type).map(proof -> new SurgeryDependencyRevision(type,proof.sourceId(),proof.revision()))).toList();
        return ReadinessSnapshot.evaluate(UUID.randomUUID(),value.getSurgeryCaseId(),
                guards.getOrDefault(SurgeryDependencyType.INDICATION,false),
                guards.getOrDefault(SurgeryDependencyType.CHECKLIST,false),
                guards.getOrDefault(SurgeryDependencyType.SURGERY_CONSENT,false),
                guards.getOrDefault(SurgeryDependencyType.ANESTHESIA_CONSENT,false),
                guards.getOrDefault(SurgeryDependencyType.TEAM_ELIGIBILITY,false),
                guards.getOrDefault(SurgeryDependencyType.SCHEDULE,false),
                guards.getOrDefault(SurgeryDependencyType.FINANCIAL_CLEARANCE,false),at,dependencies,validUntil);
    }
}
