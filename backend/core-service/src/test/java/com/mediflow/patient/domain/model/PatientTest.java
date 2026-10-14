package com.mediflow.patient.domain.model;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class PatientTest {
    @Test
    void restoreKeepsEnglishPersistenceIdentityAndMOrFGender() {
        UUID id = UUID.randomUUID();
        Patient patient = Patient.restore(id, "A", LocalDate.of(2000, 1, 1), Gender.F,
                "1", null, null, null, null, null, null);
        assertThat(patient.patientId()).isEqualTo(id);
        assertThat(patient.gender()).isEqualTo(Gender.F);
    }
}
