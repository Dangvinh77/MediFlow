package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AppointmentScheduledContractTest {
    private final UUID id = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();
    private final UUID doctor = UUID.randomUUID();
    private final UUID department = UUID.randomUUID();
    private final Instant createdAt = Instant.parse("2026-10-26T03:00:00Z");

    @Test
    void scheduledAppointmentKeepsRegistrationIdentityAndAtCounterArrival() {
        Appointment appointment = Appointment.createScheduled(id, patient, doctor, department,
                LocalDate.of(2026, 10, 27), LocalTime.of(9, 0), "counter", createdAt, createdAt);

        assertThat(appointment.getAppointmentId()).isEqualTo(id);
        assertThat(appointment.getCareContractVersion()).isEqualTo((short) 2);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.AWAITING_PAYMENT);
        assertThat(appointment.getCheckedInAt()).isEqualTo(createdAt);
    }

    @Test
    void legacyWritesCannotChangeScheduledAppointments() {
        Appointment appointment = scheduled();

        assertLegacyWriteBlocked(() -> appointment.changeStatus(AppointmentStatus.ARRIVED));
        assertLegacyWriteBlocked(() -> appointment.checkIn("OUTPATIENT_EXAM", createdAt));
        assertLegacyWriteBlocked(() -> appointment.recordExamClearance(UUID.randomUUID(), createdAt));
    }

    @Test
    void onlyScheduledStartCanEnterExamAfterConfirmationAndArrival() {
        UUID clearanceId = UUID.randomUUID();
        Appointment appointment = Appointment.restore(id, patient, doctor, department,
                LocalDate.of(2026, 10, 27), LocalTime.of(9, 0), AppointmentStatus.READY_FOR_EXAM,
                "counter", createdAt, createdAt, (short) 2, clearanceId, createdAt, null, "OUTPATIENT_EXAM",
                createdAt, null, null);

        appointment.startScheduledExam(clearanceId, createdAt.plusSeconds(600));

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_EXAM);
        assertThat(appointment.getExaminationStartedAt()).isEqualTo(createdAt.plusSeconds(600));
    }

    private Appointment scheduled() {
        return Appointment.createScheduled(id, patient, doctor, department,
                LocalDate.of(2026, 10, 27), LocalTime.of(9, 0), "counter", createdAt, createdAt);
    }

    private static void assertLegacyWriteBlocked(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(
                com.mediflow.clinical.domain.exception.InvalidClinicalDataException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("CLINICAL_REGISTRATION_FLOW_REQUIRED"));
    }
}
