package com.mediflow.pharmacy.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.domain.model.AdmissionLifecycleFact.Kind;

class AdmissionMedicationContextTest {
    private final UUID admissionId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final Instant startedAt = Instant.parse("2026-09-28T02:10:00Z");
    private final Instant medicallyDischargedAt = Instant.parse("2026-09-30T09:00:00Z");
    private final Instant closedAt = Instant.parse("2026-10-01T11:00:00Z");

    @Test
    void closeBeforeStart_thenLateStart_neverReopensAndMatchesOrderedProjection() {
        var seed = AdmissionMedicationContext.empty(admissionId, patientId);
        var closed = seed.apply(close());
        assertThat(closed.startedAt()).isNull();
        var reversed = closed.apply(start());
        assertThat(reversed).isEqualTo(seed.apply(start()).apply(close()));
        assertThatThrownBy(() -> reversed.requireActive(patientId, departmentId))
                .hasMessageContaining("not active");
    }

    @Test
    void sameBusinessFactUnderAnotherEvent_isIdempotentButConflictingStartIsRejected() {
        var active = AdmissionMedicationContext.empty(admissionId, patientId).apply(start());
        assertThat(active.apply(start())).isSameAs(active);
        assertThatThrownBy(() -> active.apply(new AdmissionLifecycleFact(Kind.STARTED,
                admissionId, patientId, departmentId, startedAt, "c".repeat(64))))
                .hasMessageContaining("changed");
        assertThat(active.version()).isOne();
    }

    @Test
    void authorizationRequiresExactPatientAndDepartment() {
        var active = AdmissionMedicationContext.empty(admissionId, patientId).apply(start());
        active.requireActive(patientId, departmentId);
        assertThatThrownBy(() -> active.requireActive(UUID.randomUUID(), departmentId))
                .hasMessageContaining("mismatch");
        assertThatThrownBy(() -> active.requireActive(patientId, UUID.randomUUID()))
                .hasMessageContaining("mismatch");
    }

    @Test
    void differentPatientAndInvalidTemporalOrderAreRejected() {
        var active = AdmissionMedicationContext.empty(admissionId, patientId).apply(start());
        assertThatThrownBy(() -> active.apply(new AdmissionLifecycleFact(Kind.CLOSED, admissionId,
                UUID.randomUUID(), null, closedAt, "b".repeat(64)))).hasMessageContaining("identity changed");
        assertThatThrownBy(() -> active.apply(new AdmissionLifecycleFact(Kind.CLOSED, admissionId,
                patientId, null, startedAt.minusSeconds(1), "b".repeat(64))))
                .hasMessageContaining("close precedes");
    }

    @Test
    void conflictingCloseIsRejectedWithoutChangingTerminalOutcome() {
        var closed = AdmissionMedicationContext.empty(admissionId, patientId).apply(close());
        assertThat(closed.apply(close())).isSameAs(closed);
        assertThatThrownBy(() -> closed.apply(new AdmissionLifecycleFact(Kind.CLOSED, admissionId,
                patientId, null, closedAt.plusSeconds(1), "c".repeat(64)))).hasMessageContaining("changed");
        assertThat(closed.closedAt()).isEqualTo(closedAt);
    }

    @Test
    void medicalDischargeBeforeStart_isDurableAndLateStartNeverRestoresEligibility() {
        var seed = AdmissionMedicationContext.empty(admissionId, patientId);
        var reversed = seed.apply(discharge()).apply(start());
        assertThat(reversed).isEqualTo(seed.apply(start()).apply(discharge()));
        assertThat(reversed.medicallyDischargedAt()).isEqualTo(medicallyDischargedAt);
        assertThatThrownBy(() -> reversed.requireActive(patientId, departmentId))
                .hasMessageContaining("not active");
        assertThat(reversed.apply(discharge())).isSameAs(reversed);
        assertThatThrownBy(() -> reversed.apply(new AdmissionLifecycleFact(Kind.MEDICALLY_DISCHARGED,
                admissionId, patientId, null, medicallyDischargedAt, "d".repeat(64))))
                .hasMessageContaining("changed");
    }

    @Test
    void closeBeforeMedicalDischargeAndStart_matchesOrderedProjection() {
        var seed = AdmissionMedicationContext.empty(admissionId, patientId);
        assertThat(seed.apply(close()).apply(discharge()).apply(start()))
                .isEqualTo(seed.apply(start()).apply(discharge()).apply(close()));
        assertThatThrownBy(() -> seed.apply(close()).apply(new AdmissionLifecycleFact(
                Kind.MEDICALLY_DISCHARGED, admissionId, patientId, null,
                closedAt.plusSeconds(1), "c".repeat(64))))
                .hasMessageContaining("close precedes medical discharge");
        assertThatThrownBy(() -> seed.apply(discharge()).apply(new AdmissionLifecycleFact(
                Kind.STARTED, admissionId, patientId, departmentId,
                medicallyDischargedAt.plusSeconds(1), "a".repeat(64))))
                .hasMessageContaining("discharge precedes admission start");
    }

    @Test
    void medicalDischarge_requiresExactPatientAndNoDepartmentField() {
        var seed = AdmissionMedicationContext.empty(admissionId, patientId);
        assertThatThrownBy(() -> seed.apply(new AdmissionLifecycleFact(Kind.MEDICALLY_DISCHARGED,
                admissionId, UUID.randomUUID(), null, medicallyDischargedAt, "c".repeat(64))))
                .hasMessageContaining("identity changed");
        assertThatThrownBy(() -> new AdmissionLifecycleFact(Kind.MEDICALLY_DISCHARGED,
                admissionId, patientId, departmentId, medicallyDischargedAt, "c".repeat(64)))
                .hasMessageContaining("Incomplete admission lifecycle fact");
    }

    private AdmissionLifecycleFact start() {
        return new AdmissionLifecycleFact(Kind.STARTED, admissionId, patientId, departmentId, startedAt, "a".repeat(64));
    }

    private AdmissionLifecycleFact close() {
        return new AdmissionLifecycleFact(Kind.CLOSED, admissionId, patientId, null, closedAt, "b".repeat(64));
    }

    private AdmissionLifecycleFact discharge() {
        return new AdmissionLifecycleFact(Kind.MEDICALLY_DISCHARGED, admissionId, patientId,
                null, medicallyDischargedAt, "c".repeat(64));
    }
}
