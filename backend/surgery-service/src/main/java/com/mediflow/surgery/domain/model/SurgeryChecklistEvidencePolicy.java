package com.mediflow.surgery.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Configured medical rules only; no implicit manual attestation, N/A or generic result-event mapping. */
public record SurgeryChecklistEvidencePolicy(UUID policyId, long revision, String procedureCode,
        UUID templateId, long templateRevision, Duration observationAge, Map<String,Rule> rules) {
    public SurgeryChecklistEvidencePolicy {
        if (policyId == null || revision < 1 || procedureCode == null || procedureCode.isBlank()
                || templateId == null || templateRevision < 1 || observationAge == null
                || observationAge.isNegative() || observationAge.isZero() || rules == null || rules.isEmpty()
                || rules.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null)
                || rules.values().stream().noneMatch(Rule::mandatory)) throw new IllegalArgumentException("Explicit medical checklist policy required");
        rules = Map.copyOf(rules);
    }

    public boolean accepts(SurgeryCase value, SurgeryChecklistSnapshot checklist, Map<UUID,Evidence> evidence, Instant at) {
        if (value == null || checklist == null || evidence == null || at == null
                || !procedureCode.equals(value.getProcedureCode()) || !value.getSurgeryCaseId().equals(checklist.surgeryCaseId())
                || !templateId.equals(checklist.templateId()) || templateRevision != checklist.templateRevision()
                || !rules.keySet().equals(checklist.items().stream().map(SurgeryChecklistItem::itemCode).collect(java.util.stream.Collectors.toSet()))) return false;
        return checklist.items().stream().allMatch(item -> {
            var rule = rules.get(item.itemCode());
            if (item.mandatory() != rule.mandatory()) return false;
            if (!rule.mandatory() && item.status() == SurgeryChecklistStatus.PENDING) return true;
            var proof = evidence.get(item.checklistItemId());
            if (proof == null || !proof.authorizedAttestation() || !rule.sources().contains(proof.source())
                    || !value.getSurgeryCaseId().equals(proof.surgeryCaseId()) || !value.getPatientId().equals(proof.patientId())
                    || !value.getCareEpisode().equals(proof.episode()) || proof.revision() != proof.currentRevision()
                    || proof.observedAt().isAfter(at) || proof.observedAt().isBefore(at.minus(observationAge))
                    || proof.validUntil() != null && !at.isBefore(proof.validUntil())) return false;
            if (!proof.sourceId().equals(item.evidenceReferenceId())
                    || !java.util.Objects.equals(proof.revision(),item.evidenceRevision())) return false;
            if (item.status() == SurgeryChecklistStatus.NOT_APPLICABLE) return !rule.mandatory() && rule.allowNotApplicable();
            return item.status() == SurgeryChecklistStatus.SATISFIED;
        });
    }

    public enum Source { MANUAL_ATTESTATION, LAB_RESULT, PHARMACY_FACT }
    public record Rule(boolean mandatory, Set<Source> sources, boolean allowNotApplicable) {
        public Rule {
            if (sources == null || sources.isEmpty() || sources.stream().anyMatch(java.util.Objects::isNull)
                    || mandatory && allowNotApplicable) throw new IllegalArgumentException("Invalid explicit checklist rule");
            sources = Set.copyOf(sources);
        }
    }
    public record Evidence(UUID surgeryCaseId,UUID patientId,CareEpisode episode,Source source,UUID sourceId,
            long revision,long currentRevision,Instant observedAt,Instant validUntil,boolean authorizedAttestation) {
        public Evidence {
            if (surgeryCaseId == null || patientId == null || episode == null || source == null || sourceId == null
                    || revision < 0 || currentRevision < 0 || observedAt == null
                    || validUntil != null && !validUntil.isAfter(observedAt)) throw new IllegalArgumentException("Invalid clinical source evidence");
        }
    }
}
