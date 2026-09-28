package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Immutable evidence of the guards checked at one readiness decision. */
public record ReadinessSnapshot(
        UUID readinessSnapshotId,
        UUID surgeryCaseId,
        boolean indicationValid,
        boolean mandatoryChecklistComplete,
        boolean surgeryConsentActive,
        boolean anesthesiaConsentActive,
        boolean teamEligible,
        boolean scheduleConfirmed,
        boolean financialClearanceValid,
        Instant evaluatedAt,
        List<SurgeryDependencyRevision> dependencyRevisions,
        Instant validUntil,
        List<String> blockingReasons) {

    public ReadinessSnapshot {
        if (readinessSnapshotId == null || surgeryCaseId == null || evaluatedAt == null
                || dependencyRevisions == null || blockingReasons == null
                || (validUntil != null && !validUntil.isAfter(evaluatedAt))) {
            throw new SurgeryRuleException(
                    "SURGERY_READINESS_SNAPSHOT_INVALID",
                    "Snapshot readiness phải có mã, ca phẫu thuật và thời điểm đánh giá");
        }
        if (dependencyRevisions.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("SURGERY_READINESS_DEPENDENCY_INVALID");
        }
        dependencyRevisions = List.copyOf(dependencyRevisions);
        if (blockingReasons.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalid("SURGERY_READINESS_REASON_INVALID");
        }
        blockingReasons = List.copyOf(blockingReasons);
        Set<SurgeryDependencyType> dependencyTypes = new HashSet<>();
        for (SurgeryDependencyRevision dependency : dependencyRevisions) {
            if (!dependencyTypes.add(dependency.dependencyType())) {
                throw invalid("SURGERY_READINESS_DEPENDENCY_DUPLICATE");
            }
        }
        List<String> expectedReasons = deriveReasons(
                indicationValid, mandatoryChecklistComplete, surgeryConsentActive,
                anesthesiaConsentActive, teamEligible,
                scheduleConfirmed, financialClearanceValid, dependencyTypes);
        if (!expectedReasons.equals(blockingReasons)) {
            throw invalid("SURGERY_READINESS_REASON_MISMATCH");
        }
    }

    public static ReadinessSnapshot evaluate(
            UUID snapshotId,
            UUID caseId,
            boolean indicationValid,
            boolean checklistComplete,
            boolean surgeryConsentActive,
            boolean anesthesiaConsentActive,
            boolean teamEligible,
            boolean scheduleValid,
            boolean financialClearanceValid,
            Instant evaluatedAt,
            List<SurgeryDependencyRevision> dependencyRevisions,
            Instant validUntil) {
        if (dependencyRevisions == null) {
            throw invalid("SURGERY_READINESS_DEPENDENCIES_REQUIRED");
        }
        Set<SurgeryDependencyType> types = new HashSet<>();
        dependencyRevisions.forEach(dependency -> {
            if (dependency == null || !types.add(dependency.dependencyType())) {
                throw invalid("SURGERY_READINESS_DEPENDENCY_DUPLICATE");
            }
        });
        return new ReadinessSnapshot(snapshotId, caseId, indicationValid, checklistComplete,
                surgeryConsentActive, anesthesiaConsentActive, teamEligible, scheduleValid,
                financialClearanceValid, evaluatedAt, dependencyRevisions, validUntil,
                deriveReasons(indicationValid, checklistComplete, surgeryConsentActive,
                        anesthesiaConsentActive, teamEligible, scheduleValid,
                        financialClearanceValid, types));
    }

    /** V1 requires both separately recorded consents and never uses an emergency override. */
    public boolean isReady() {
        return blockingReasons.isEmpty()
                && indicationValid
                && mandatoryChecklistComplete
                && surgeryConsentActive
                && anesthesiaConsentActive
                && teamEligible
                && scheduleConfirmed
                && financialClearanceValid;
    }

    public boolean isValidAt(Instant at) {
        return at != null && isReady() && (validUntil == null || at.isBefore(validUntil));
    }

    public UUID snapshotId() {
        return readinessSnapshotId;
    }

    public UUID surgeryCaseId() {
        return surgeryCaseId;
    }

    public Instant evaluatedAt() {
        return evaluatedAt;
    }

    private static List<String> deriveReasons(
            boolean indicationValid,
            boolean checklistComplete,
            boolean surgeryConsentActive,
            boolean anesthesiaConsentActive,
            boolean teamEligible,
            boolean scheduleValid,
            boolean financialClearanceValid,
            Set<SurgeryDependencyType> dependencies) {
        List<String> reasons = new ArrayList<>();
        if (!indicationValid) reasons.add("INDICATION_INVALID");
        if (!checklistComplete) reasons.add("CHECKLIST_INCOMPLETE");
        if (!surgeryConsentActive) reasons.add("SURGERY_CONSENT_MISSING");
        if (!anesthesiaConsentActive) reasons.add("ANESTHESIA_CONSENT_MISSING");
        if (!teamEligible) reasons.add("TEAM_INELIGIBLE");
        if (!scheduleValid) reasons.add("SCHEDULE_INVALID");
        if (!financialClearanceValid) reasons.add("FINANCIAL_CLEARANCE_INVALID");
        if (!dependencies.containsAll(Set.of(SurgeryDependencyType.values()))) {
            reasons.add("READINESS_DEPENDENCY_SNAPSHOT_INCOMPLETE");
        }
        return List.copyOf(reasons);
    }

    private static SurgeryRuleException invalid(String code) {
        return new SurgeryRuleException(code, "Thông tin đánh giá readiness không nhất quán");
    }
}
