package com.mediflow.surgery.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Explicit procedure policy only: no seeded clinical items, participant counts or legal approval. */
public record SurgerySafetyPausePolicy(UUID policyId, long revision, String procedureCode,
        UUID approvalReferenceId, List<PhaseRule> phases) {
    public SurgerySafetyPausePolicy {
        if (policyId == null || revision < 1 || !code(procedureCode) || approvalReferenceId == null
                || phases == null || phases.size() != SurgerySafetyPhase.values().length
                || phases.stream().anyMatch(java.util.Objects::isNull)
                || phases.stream().map(PhaseRule::phase).distinct().count() != phases.size()) {
            throw new IllegalArgumentException("An explicit three-phase procedure policy is required");
        }
        phases = phases.stream().sorted(Comparator.comparing(PhaseRule::phase)).toList();
    }

    public PhaseRule rule(SurgerySafetyPhase phase) {
        return phases.stream().filter(value -> value.phase() == phase).findFirst().orElseThrow();
    }

    private static boolean code(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    }

    public record PhaseRule(SurgerySafetyPhase phase, Set<SurgeryTeamRole> requiredParticipantRoles,
            List<ItemRule> items) {
        public PhaseRule {
            if (phase == null || requiredParticipantRoles == null || requiredParticipantRoles.isEmpty()
                    || requiredParticipantRoles.stream().anyMatch(java.util.Objects::isNull) || items == null || items.isEmpty()
                    || items.size() > 100 || items.stream().anyMatch(java.util.Objects::isNull)
                    || items.stream().map(ItemRule::itemCode).distinct().count() != items.size()) {
                throw new IllegalArgumentException("Explicit phase items and participant roles required");
            }
            requiredParticipantRoles = java.util.Collections.unmodifiableSet(
                    java.util.EnumSet.copyOf(requiredParticipantRoles));
            items = items.stream().sorted(Comparator.comparing(ItemRule::itemCode)).toList();
        }
    }

    public record ItemRule(String itemCode, Set<String> permittedNotApplicableReasons) {
        public ItemRule {
            if (!code(itemCode) || permittedNotApplicableReasons == null
                    || permittedNotApplicableReasons.size() > 32
                    || permittedNotApplicableReasons.stream().anyMatch(value -> !code(value))) {
                throw new IllegalArgumentException("Explicit item applicability policy required");
            }
            permittedNotApplicableReasons = java.util.Collections.unmodifiableSet(
                    new java.util.TreeSet<>(permittedNotApplicableReasons));
        }
    }
}
