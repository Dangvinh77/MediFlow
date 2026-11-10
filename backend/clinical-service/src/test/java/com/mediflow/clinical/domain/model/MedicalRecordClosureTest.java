package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import org.junit.jupiter.api.Test;

class MedicalRecordClosureTest {
    private static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");
    private final UUID doctor = UUID.randomUUID();

    private MedicalRecord open() {
        return MedicalRecord.openForAppointment(UUID.randomUUID(), doctor, UUID.randomUUID(), LocalDate.of(2026, 11, 10),
                null, UUID.randomUUID(), AT.minusSeconds(600));
    }

    private static void assertCode(Runnable command, String code) {
        assertThatThrownBy(command::run).isInstanceOfSatisfying(InvalidClinicalDataException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    @Test void closeRecordsReasonNoteActorAndTime() {
        MedicalRecord record = open();
        record.closeWithoutConclusion(RecordClosureReason.PATIENT_LEFT, "Bệnh nhân tự ý về, đã dặn tái khám", doctor, AT);
        assertThat(record.getStatus()).isEqualTo(MedicalRecordStatus.CLOSED_WITHOUT_CONCLUSION);
        assertThat(record.getClosureReason()).isEqualTo(RecordClosureReason.PATIENT_LEFT);
        assertThat(record.getClosureNote()).isEqualTo("Bệnh nhân tự ý về, đã dặn tái khám");
        assertThat(record.getClosedByStaffId()).isEqualTo(doctor);
        assertThat(record.getClosedAt()).isEqualTo(AT);
        assertThat(record.getDisposition()).isNull();
        assertThat(record.getCompletedAt()).isNull();
    }

    @Test void closeNeedsNoDiagnosis() {
        MedicalRecord record = open();
        record.closeWithoutConclusion(RecordClosureReason.PATIENT_REFUSED_EXAM, "Từ chối khám", doctor, AT);
        assertThat(record.getDiagnoses()).isEmpty();
    }

    @Test void closedRecordIsImmutable() {
        MedicalRecord record = open();
        record.closeWithoutConclusion(RecordClosureReason.OTHER, "Lý do khác", doctor, AT);
        assertCode(() -> record.update("Sửa"), "CLINICAL_INVALID_STATUS_TRANSITION");
        assertCode(() -> record.complete(RecordDisposition.OTHER, null, AT), "CLINICAL_INVALID_STATUS_TRANSITION");
        assertCode(() -> record.closeWithoutConclusion(RecordClosureReason.OTHER, "Lần hai", doctor, AT), "CLINICAL_INVALID_STATUS_TRANSITION");
    }

    @Test void closeRejectsBlankNote() {
        assertCode(() -> open().closeWithoutConclusion(RecordClosureReason.OTHER, "   ", doctor, AT), "RECORD_CLOSURE_INVALID");
    }

    @Test void closeRejectsNoteOver1000() {
        assertCode(() -> open().closeWithoutConclusion(RecordClosureReason.OTHER, "x".repeat(1001), doctor, AT), "RECORD_CLOSURE_INVALID");
    }

    @Test void closeRequiresReasonAndActor() {
        assertCode(() -> open().closeWithoutConclusion(null, "Có ghi chú", doctor, AT), "RECORD_CLOSURE_INVALID");
        assertCode(() -> open().closeWithoutConclusion(RecordClosureReason.OTHER, "Có ghi chú", null, AT), "RECORD_CLOSURE_INVALID");
    }

    @Test void newRecordCarriesOnlyTheLinkToItsSource() {
        UUID source = UUID.randomUUID();
        MedicalRecord record = MedicalRecord.openForAppointment(UUID.randomUUID(), doctor, UUID.randomUUID(),
                LocalDate.of(2026, 11, 10), null, UUID.randomUUID(), AT, source);
        assertThat(record.getPreviousRecordId()).isEqualTo(source);
        assertThat(record.getDiagnoses()).isEmpty();
        assertThat(record.getSymptoms()).isNull();
    }
}
