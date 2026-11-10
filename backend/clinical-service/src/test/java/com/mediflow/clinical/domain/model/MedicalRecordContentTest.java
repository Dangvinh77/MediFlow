package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;

class MedicalRecordContentTest {
    private static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");

    private static MedicalRecord open() {
        return MedicalRecord.openForAppointment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.parse("2026-11-10"), "Sốt", UUID.randomUUID(), AT);
    }

    private static ClinicalContent content(String complaint, String illness) {
        return new ClinicalContent(complaint, illness, null, null, null, null);
    }

    @Test void contentSectionsAreStored() {
        MedicalRecord record = open();
        record.update("Sốt", new ClinicalContent("Ho", "Ho ba ngày", "Hen", "Penicillin", "Phổi ran", "Uống nhiều nước"));
        assertThat(record.getContent()).isEqualTo(
                new ClinicalContent("Ho", "Ho ba ngày", "Hen", "Penicillin", "Phổi ran", "Uống nhiều nước"));
        assertThat(record.getChiefComplaint()).isEqualTo("Ho");
        assertThat(record.getAdvice()).isEqualTo("Uống nhiều nước");
    }

    @Test void nullKeepsSectionEmptyClears() {
        MedicalRecord record = open();
        record.update("Sốt", content("Ho", "Ho ba ngày"));
        record.update("Sốt", content(null, ""));
        assertThat(record.getChiefComplaint()).isEqualTo("Ho");
        assertThat(record.getPresentIllness()).isNull();
    }

    @Test void sectionOver4000IsRefused() {
        MedicalRecord record = open();
        assertThatThrownBy(() -> record.update("Sốt", content("x".repeat(4001), null)))
                .isInstanceOf(InvalidClinicalDataException.class)
                .extracting("code").isEqualTo("RECORD_CONTENT_INVALID");
        assertThat(record.getContent()).isEqualTo(ClinicalContent.EMPTY);
    }

    @Test void closedRecordContentIsImmutable() {
        MedicalRecord record = open();
        record.closeWithoutConclusion(RecordClosureReason.PATIENT_LEFT, "Tự về", UUID.randomUUID(), AT);
        assertThatThrownBy(() -> record.update("Sốt", content("Ho", null)))
                .isInstanceOf(InvalidClinicalDataException.class);
        assertThat(record.getContent()).isEqualTo(ClinicalContent.EMPTY);
    }

    @Test void oldUpdateWithOnlySymptomsKeepsContent() {
        MedicalRecord record = open();
        record.update("Sốt", content("Ho", "Ho ba ngày"));
        record.update("Sốt cao");
        assertThat(record.getSymptoms()).isEqualTo("Sốt cao");
        assertThat(record.getContent()).isEqualTo(content("Ho", "Ho ba ngày"));
    }
}
