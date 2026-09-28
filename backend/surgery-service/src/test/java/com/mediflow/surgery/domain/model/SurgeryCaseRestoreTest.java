package com.mediflow.surgery.domain.model;

import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurgeryCaseRestoreTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-28T01:00:00Z");
    private static final String CORRELATION_ID = "restore-test";
    private static final SurgeryAuditActor HUMAN = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());

    @Test
    void restore_completedCase_preservesStateRevisionAuditAndReadinessWithoutCreatingHistory() {
        SurgeryCase original = completedCase();
        List<SurgeryStateChange> persistedHistory = original.getStatusHistory();

        SurgeryCase restored = restore(original, persistedHistory);

        assertThat(restored.getSurgeryCaseId()).isEqualTo(original.getSurgeryCaseId());
        assertThat(restored.getStatus()).isEqualTo(SurgeryStatus.COMPLETED);
        assertThat(restored.getRevision()).isEqualTo(original.getRevision());
        assertThat(restored.getStartedAt()).isEqualTo(original.getStartedAt());
        assertThat(restored.getCompletedAt()).isEqualTo(original.getCompletedAt());
        assertThat(restored.getReadinessSnapshot()).isEqualTo(original.getReadinessSnapshot());
        assertThat(restored.getStatusHistory()).containsExactlyElementsOf(persistedHistory);

        assertThatThrownBy(() -> restored.cancel(HUMAN, CORRELATION_ID,
                REQUESTED_AT.plusSeconds(100), "RETRY"))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_INVALID_TRANSITION");
        assertThat(restored.getStatusHistory()).hasSize(persistedHistory.size());
    }

    @Test
    void restore_inconsistentStatusHistory_rejectsPersistedAggregate() {
        SurgeryCase original = completedCase();

        assertThatThrownBy(() -> restore(original, original.getStatusHistory().subList(0, 1)))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_AGGREGATE_RESTORE_INVALID");
    }

    @Test
    void restore_invalidBusinessRevision_rejectsPersistedAggregate() {
        SurgeryCase original = completedCase();

        assertThatThrownBy(() -> SurgeryCase.restore(original.getSurgeryCaseId(),
                original.getSurgeryRequestId(), original.getCareEpisode(), original.getPatientId(),
                original.getDepartmentId(), original.getRequestedBy(), original.getProcedureCode(),
                original.getIndication(), original.getPriority(), original.getRequestedAt(),
                original.getStatus(), original.getReadinessSnapshot(), original.getReadyAt(),
                original.getStartedAt(), original.getCompletedAt(), null, null, null,
                0, original.getStatusHistory(), original.getRevisionHistory()))
                .isInstanceOf(SurgeryRuleException.class)
                .hasFieldOrPropertyWithValue("code", "SURGERY_AGGREGATE_RESTORE_INVALID");
    }

    @Test
    void childMutation_advancesBusinessRevisionAndRestoresWithoutInventingStatusTransition() {
        UUID admissionId = UUID.randomUUID();
        SurgeryCase surgeryCase = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.ADMISSION, admissionId, admissionId, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PROC-002",
                "Clinical indication", SurgeryPriority.URGENT, REQUESTED_AT, HUMAN, CORRELATION_ID);
        surgeryCase.recordBusinessMutation(HUMAN, CORRELATION_ID,
                REQUESTED_AT.plusSeconds(1), "CHECKLIST_SNAPSHOT_CREATED");
        surgeryCase.beginPreop(HUMAN, CORRELATION_ID, REQUESTED_AT.plusSeconds(2));

        SurgeryCase restored = restore(surgeryCase, surgeryCase.getStatusHistory());

        assertThat(restored.getRevision()).isEqualTo(2);
        assertThat(restored.getStatusHistory()).hasSize(2);
        assertThat(restored.getRevisionHistory()).extracting(SurgeryCaseAuditEntry::changeCode)
                .containsExactly("CASE_CREATED", "CHECKLIST_SNAPSHOT_CREATED", "PREOP_STARTED");
        assertThat(restored.getRevisionHistory().get(1).changesStatus()).isFalse();
    }

    private static SurgeryCase completedCase() {
        UUID caseId = UUID.randomUUID();
        SurgeryCase surgeryCase = SurgeryCase.create(caseId, UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PROC-001",
                "Clinical indication", SurgeryPriority.ROUTINE, REQUESTED_AT, HUMAN, CORRELATION_ID);
        surgeryCase.beginPreop(HUMAN, CORRELATION_ID, REQUESTED_AT.plusSeconds(1));
        ReadinessSnapshot readiness = ReadinessSnapshot.evaluate(UUID.randomUUID(), caseId,
                true, true, true, true, true, true, true, REQUESTED_AT.plusSeconds(2),
                SurgeryLocalModelsTest.dependencies(), null);
        surgeryCase.markReady(readiness, HUMAN, CORRELATION_ID);
        surgeryCase.finalizeSchedule(HUMAN, CORRELATION_ID, REQUESTED_AT.plusSeconds(3));
        surgeryCase.start(readiness, HUMAN, CORRELATION_ID, REQUESTED_AT.plusSeconds(4));
        surgeryCase.complete(HUMAN, CORRELATION_ID, REQUESTED_AT.plusSeconds(5));
        return surgeryCase;
    }

    private static SurgeryCase restore(SurgeryCase original, List<SurgeryStateChange> history) {
        return SurgeryCase.restore(original.getSurgeryCaseId(), original.getSurgeryRequestId(),
                original.getCareEpisode(), original.getPatientId(), original.getDepartmentId(),
                original.getRequestedBy(), original.getProcedureCode(), original.getIndication(),
                original.getPriority(), original.getRequestedAt(), original.getStatus(),
                original.getReadinessSnapshot(), original.getReadyAt(),
                original.getStartedAt(), original.getCompletedAt(), original.getCancelledAt(),
                original.getCancelledBy(), original.getCancellationReason(), original.getRevision(), history,
                original.getRevisionHistory());
    }
}
