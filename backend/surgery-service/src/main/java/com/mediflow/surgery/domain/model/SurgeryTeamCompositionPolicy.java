package com.mediflow.surgery.domain.model;

import java.util.Map;
import java.util.UUID;

/** Explicit versioned clinical configuration, never a job-title or login-role inference. */
public record SurgeryTeamCompositionPolicy(UUID policyId, String procedureCode, long revision,
        Map<SurgeryTeamRole, Cardinality> roles) {
    public SurgeryTeamCompositionPolicy {
        if (policyId == null || procedureCode == null || procedureCode.isBlank() || revision < 1
                || roles == null || roles.isEmpty() || roles.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)
                || roles.values().stream().mapToInt(Cardinality::minimum).sum() == 0) {
            throw new IllegalArgumentException("An explicit clinical team policy is required");
        }
        roles = Map.copyOf(roles);
    }

    public boolean accepts(String procedure, SurgerySchedule schedule) {
        if (!procedureCode.equals(procedure) || schedule == null) return false;
        for (var member : schedule.teamAssignments()) if (!roles.containsKey(member.role())) return false;
        return roles.entrySet().stream().allMatch(entry -> {
            long count = schedule.teamAssignments().stream().filter(member -> member.role() == entry.getKey()).count();
            return count >= entry.getValue().minimum() && count <= entry.getValue().maximum();
        });
    }

    public record Cardinality(int minimum, int maximum) {
        public Cardinality {
            if (minimum < 0 || maximum < 1 || maximum < minimum || maximum > 100) {
                throw new IllegalArgumentException("Invalid configured team cardinality");
            }
        }
    }
}
