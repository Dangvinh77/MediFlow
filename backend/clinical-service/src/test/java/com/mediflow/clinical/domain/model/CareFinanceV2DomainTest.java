package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CareFinanceV2DomainTest {

    private static final Instant NOW = Instant.parse("2026-09-27T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void checkIn_pendingAppointment_opensPaymentGateAndRecordsPriceSnapshot() {
        Appointment appointment = appointment();

        appointment.checkIn("OUTPATIENT_EXAM", NOW);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.AWAITING_PAYMENT);
        assertThat(appointment.getCareContractVersion()).isEqualTo((short) 1);
        assertThat(appointment.getExamPriceCode()).isEqualTo("OUTPATIENT_EXAM");
        assertThat(appointment.getCheckedInAt()).isEqualTo(NOW);
    }

    @Test
    void startExam_arrivedWithoutClearanceOrOverride_rejects() {
        Appointment appointment = appointment();
        appointment.checkIn("OUTPATIENT_EXAM", NOW);

        assertThatThrownBy(() -> appointment.startExam(null, null, NOW))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_EXAM_CLEARANCE_REQUIRED");
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.AWAITING_PAYMENT);
    }

    @Test
    void genericStatusChange_readyForExam_rejects() {
        Appointment appointment = appointment();

        assertThatThrownBy(() -> appointment.changeStatus(AppointmentStatus.READY_FOR_EXAM))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_INVALID_STATUS_TRANSITION");
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.PENDING);
    }

    @Test
    void openRecord_withoutDiagnosis_remainsOpenUntilCompletion() {
        MedicalRecord record = MedicalRecord.openForAppointment(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(CLOCK),
                "Fever", UUID.randomUUID(), NOW);

        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.OPEN);
        assertThat(record.getDiagnoses()).isEmpty();
        assertThatThrownBy(() -> record.complete(RecordDisposition.OUTPATIENT_FOLLOW_UP, null, NOW))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_DIAGNOSIS_REQUIRED");
    }

    @Test
    void completeRecord_withDiagnosisAndDisposition_recordsCompletionOnce() {
        MedicalRecord record = MedicalRecord.openForAppointment(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(CLOCK),
                "Fever", UUID.randomUUID(), NOW);
        record.addDiagnosis(Diagnosis.create("Influenza", null, "J10"));

        record.complete(RecordDisposition.ADMISSION, "Needs inpatient observation", NOW);

        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.COMPLETED);
        assertThat(record.getDisposition()).isEqualTo(RecordDisposition.ADMISSION);
        assertThat(record.getDispositionNote()).isEqualTo("Needs inpatient observation");
        assertThat(record.getCompletedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> record.complete(RecordDisposition.ADMISSION, null, NOW.plusSeconds(1)))
                .hasFieldOrPropertyWithValue("code", "CLINICAL_INVALID_STATUS_TRANSITION");
    }

    private Appointment appointment() {
        return Appointment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(CLOCK), LocalTime.NOON, "Check-up", CLOCK);
    }
}
