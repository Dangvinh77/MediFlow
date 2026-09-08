package com.mediflow.clinical.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MedicalRecordTest {
    private MedicalRecord create(List<Diagnosis> diagnoses) {
        return MedicalRecord.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), "Cough", null, diagnoses);
    }
    private Diagnosis diagnosis() { return Diagnosis.create("Flu", "Clinical diagnosis", "J10.1"); }

    @Test void create_noDiagnosis_rejects() {
        assertThatThrownBy(() -> create(List.of())).hasFieldOrPropertyWithValue("code", "RECORD_NO_DIAGNOSIS");
        assertThatThrownBy(() -> create(null)).hasFieldOrPropertyWithValue("code", "RECORD_NO_DIAGNOSIS");
    }

    @Test void create_diagnoses_copiesAndProtectsList() {
        List<Diagnosis> input = new ArrayList<>(List.of(diagnosis()));
        MedicalRecord record = create(input);
        input.clear();
        assertThat(record.getDiagnoses()).hasSize(1);
        assertThatThrownBy(() -> record.getDiagnoses().clear()).isInstanceOf(UnsupportedOperationException.class);
        record.addDiagnosis(Diagnosis.create("Fever", null, "R50"));
        assertThat(record.getDiagnoses()).hasSize(2);
    }

    @Test void addDiagnosis_null_rejectsWithoutMutation() {
        MedicalRecord record = create(List.of(diagnosis()));
        assertThatThrownBy(() -> record.addDiagnosis(null)).hasFieldOrPropertyWithValue("code", "RECORD_NO_DIAGNOSIS");
        assertThat(record.getDiagnoses()).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"j10", "J1", "J10.123", "J10X", ""})
    void createDiagnosis_invalidIcd_rejects(String icd) {
        assertThatThrownBy(() -> Diagnosis.create("Flu", null, icd)).hasFieldOrPropertyWithValue("code", "DIAGNOSIS_ICD_INVALID");
    }

    @Test void createDiagnosis_optionalIcd_acceptsNull() {
        assertThat(Diagnosis.create("Flu", null, null).getIcdCode()).isNull();
    }

    @Test void createDiagnosis_blankName_rejects() {
        assertThatThrownBy(() -> Diagnosis.create(" ", null, null)).hasFieldOrPropertyWithValue("code", "DIAGNOSIS_NAME_REQUIRED");
    }

    @Test void create_nullDiagnosisElement_rejects() {
        assertThatThrownBy(() -> create(Arrays.asList(diagnosis(), null)))
                .hasFieldOrPropertyWithValue("code", "RECORD_NO_DIAGNOSIS");
    }

    @Test void update_changesSymptomsWithoutReplacingDiagnoses() {
        MedicalRecord record = create(List.of(diagnosis()));
        record.update("Improved");
        assertThat(record.getSymptoms()).isEqualTo("Improved");
        assertThat(record.getDiagnoses()).hasSize(1);
        assertThat(record.getUpdatedAt()).isNotNull();
    }

    @Test void create_missingReferencesOrDate_returnsTypedErrors() {
        assertThatThrownBy(() -> MedicalRecord.create(null, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), null, null, List.of(diagnosis())))
                .hasFieldOrPropertyWithValue("code", "RECORD_REF_REQUIRED");
        assertThatThrownBy(() -> MedicalRecord.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, null, List.of(diagnosis())))
                .hasFieldOrPropertyWithValue("code", "RECORD_DATE_REQUIRED");
    }

    @Test void restore_preservesPersistedIdsAndTimestamps() {
        UUID recordId = UUID.randomUUID();
        UUID diagnosisId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        var created = java.time.Instant.parse("2020-01-01T00:00:00Z");
        var record = MedicalRecord.restore(recordId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2020, 1, 1), null, appointmentId,
                List.of(Diagnosis.restore(diagnosisId, "Flu", null, "J10")), created, null);
        assertThat(record.getRecordId()).isEqualTo(recordId);
        assertThat(record.getAppointmentId()).isEqualTo(appointmentId);
        assertThat(record.getCreatedAt()).isEqualTo(created);
        assertThat(record.getDiagnoses().getFirst().getDiagnosisId()).isEqualTo(diagnosisId);
    }
}
