package com.mediflow.surgery.application.dto;

import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Internal authority-port response, NOT an HTTP request or a cross-service wire contract. */
public record SurgeryReadinessEvidence(UUID surgeryCaseId, UUID patientId, UUID departmentId,
        CareEpisode episode, long caseRevision, UUID scheduleId, long scheduleRevision, List<Proof> proofs) {
    public SurgeryReadinessEvidence {
        if (surgeryCaseId == null || patientId == null || departmentId == null || episode == null
                || caseRevision < 0 || scheduleId == null || scheduleRevision < 1 || proofs == null) {
            throw new IllegalArgumentException("Readiness evidence must identify its exact observed context");
        }
        var types = new HashSet<SurgeryDependencyType>();
        for (var proof : proofs) if (proof == null || !types.add(proof.type())) {
            throw new IllegalArgumentException("Duplicate or null readiness proof");
        }
        proofs = List.copyOf(proofs);
    }

    public enum Decision { SATISFIED, UNSATISFIED, UNVERIFIABLE }

    /** revision is a business source revision, not event envelope version. */
    public record Proof(SurgeryDependencyType type, UUID sourceId, long revision, Decision decision,
            Instant observedAt, Instant validFrom, Instant validUntil) {
        public Proof {
            if (type == null || sourceId == null || revision < 0 || decision == null || observedAt == null
                    || validFrom == null || validUntil != null && !validUntil.isAfter(validFrom)) {
                throw new IllegalArgumentException("Invalid source proof");
            }
        }
    }
}
