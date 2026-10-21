package com.mediflow.patient.domain.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class PatientTest {
    @Test
    void rejectsInvalidIdentityCorrectionBeforeChangingAnyFields() {
        Patient patient = Patient.create("A", LocalDate.of(1990, 1, 1), Gender.M,
                "001", null, null, null, null);
        for (String identity : new String[] {null, "", "   ", "1".repeat(21)}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> patient.update(
                    "B", patient.dateOfBirth(), Gender.F, null, null, null, null, identity))
                    .isInstanceOf(com.mediflow.patient.domain.exception.InvalidPatientDataException.class);
            assertThat(patient.fullName()).isEqualTo("A");
            assertThat(patient.identityNumber()).isEqualTo("001");
        }
    }

    @Test
    void restoreKeepsEnglishPersistenceIdentityAndMOrFGender() {
        UUID id = UUID.randomUUID();
        Patient patient = Patient.restore(id, "A", LocalDate.of(2000, 1, 1), Gender.F,
                "1", null, null, null, null, null, null);
        assertThat(patient.patientId()).isEqualTo(id);
        assertThat(patient.gender()).isEqualTo(Gender.F);
    }
}
