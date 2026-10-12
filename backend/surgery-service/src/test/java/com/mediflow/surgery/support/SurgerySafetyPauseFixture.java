package com.mediflow.surgery.support;

import com.mediflow.surgery.domain.model.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Synthetic, procedure-specific test policy. Never a clinical catalogue or authority provider. */
public final class SurgerySafetyPauseFixture {
    public static final Instant NOW = Instant.parse("2026-10-12T09:00:00.123456789Z");
    private static final UUID POLICY_ID = UUID.fromString("00000000-0000-4000-8000-000000000111");
    private static final UUID APPROVAL_ID = UUID.fromString("00000000-0000-4000-8000-000000000112");
    private static final UUID STAFF_ID = UUID.fromString("00000000-0000-4000-8000-000000000113");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-4000-8000-000000000114");
    private SurgerySafetyPauseFixture() { }

    public static SurgerySafetyPausePolicy policy() {
        return new SurgerySafetyPausePolicy(POLICY_ID, 1, "TEST-PROC", APPROVAL_ID,
                Arrays.stream(SurgerySafetyPhase.values()).map(phase -> new SurgerySafetyPausePolicy.PhaseRule(phase,
                        Set.of(SurgeryTeamRole.PRIMARY_SURGEON), List.of(
                        new SurgerySafetyPausePolicy.ItemRule("TEST_ITEM", Set.of("TEST_NOT_REQUIRED"))))).toList());
    }

    public static SurgerySafetyPause pause(UUID caseId, UUID scheduleId, SurgerySafetyPhase phase, long caseRevision) {
        return new SurgerySafetyPause(UUID.randomUUID(), UUID.randomUUID(), caseId, caseRevision, scheduleId, 1,
                policy(), phase, NOW.plusSeconds(phase.ordinal()), NOW.plusSeconds(10),
                SurgeryAuditActor.human(ACCOUNT_ID, STAFF_ID), "test-safety-pause", List.of(
                new SurgerySafetyPause.Participant(STAFF_ID, SurgeryTeamRole.PRIMARY_SURGEON, APPROVAL_ID, 1)), List.of(
                new SurgerySafetyPause.Decision("TEST_ITEM", SurgeryChecklistStatus.SATISFIED, APPROVAL_ID, 1, null, null)));
    }

    public static SurgerySafetyPause copy(SurgerySafetyPause value, UUID pauseId, UUID commandId,
            SurgerySafetyPausePolicy policy, SurgerySafetyPhase phase, Instant observedAt, Instant recordedAt,
            List<SurgerySafetyPause.Decision> decisions) {
        return new SurgerySafetyPause(pauseId, commandId, value.surgeryCaseId(), value.expectedCaseRevision(),
                value.scheduleId(), value.scheduleRevision(), policy, phase, observedAt, recordedAt, value.recorder(),
                value.correlationId(), value.participants(), decisions);
    }
}
