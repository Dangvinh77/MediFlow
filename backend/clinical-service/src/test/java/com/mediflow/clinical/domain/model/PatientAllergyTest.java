package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PatientAllergyTest {
    static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");
    final UUID patient = UUID.randomUUID();
    final UUID staff = UUID.randomUUID();

    @Test void substanceRequiredMax200() {
        assertThatThrownBy(() -> PatientAllergy.record(patient, " ", null, AllergySeverity.MILD, null, staff, AT))
                .extracting("code").isEqualTo("ALLERGY_INVALID");
        assertThatThrownBy(() -> PatientAllergy.record(patient, "x".repeat(201), null, AllergySeverity.MILD, null, staff, AT))
                .extracting("code").isEqualTo("ALLERGY_INVALID");
        assertThatThrownBy(() -> PatientAllergy.record(patient, "Penicillin", "x".repeat(501), AllergySeverity.MILD, null, staff, AT))
                .extracting("code").isEqualTo("ALLERGY_INVALID");
        assertThat(PatientAllergy.record(patient, "x".repeat(200), null, null, null, staff, AT).getSeverity())
                .isEqualTo(AllergySeverity.UNKNOWN);
    }

    @Test void substanceKeyIgnoresCaseAndExtraSpaces() {
        assertThat(PatientAllergy.key("  Penicillin   G ")).isEqualTo(PatientAllergy.key("penicillin g"));
    }

    @Test void statusChangeNeedsReason() {
        PatientAllergy allergy = PatientAllergy.record(patient, "Penicillin", "Nổi mẩn", AllergySeverity.MODERATE, null, staff, AT);
        assertThatThrownBy(() -> allergy.changeStatus(AllergyStatus.RESOLVED, " ", staff, AT))
                .extracting("code").isEqualTo("ALLERGY_INVALID");
        assertThatThrownBy(() -> allergy.changeStatus(AllergyStatus.ACTIVE, "Lý do", staff, AT))
                .extracting("code").isEqualTo("ALLERGY_INVALID");
    }

    @Test void enteredInErrorMovesToInactive() {
        PatientAllergy allergy = PatientAllergy.record(patient, "Penicillin", null, AllergySeverity.SEVERE, null, staff, AT);
        allergy.changeStatus(AllergyStatus.ENTERED_IN_ERROR, "Nhập nhầm bệnh nhân", staff, AT);
        assertThat(allergy.getStatus()).isEqualTo(AllergyStatus.ENTERED_IN_ERROR);
        assertThat(allergy.isActive()).isFalse();
        assertThat(allergy.getStatusChangedByStaffId()).isEqualTo(staff);
        assertThatThrownBy(() -> allergy.changeStatus(AllergyStatus.RESOLVED, "Lần nữa", staff, AT))
                .extracting("code").isEqualTo("ALLERGY_INVALID");
    }
}
