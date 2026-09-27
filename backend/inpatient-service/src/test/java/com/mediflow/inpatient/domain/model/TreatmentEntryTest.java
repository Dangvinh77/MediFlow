package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import com.mediflow.inpatient.domain.model.enums.TreatmentEntryType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TreatmentEntryTest {

    @Test
    void correctionAppendsNewEntryAndKeepsOriginalImmutable() {
        UUID admissionId = UUID.randomUUID();
        TreatmentEntry original = TreatmentEntry.create(admissionId, TreatmentEntryType.NOTE,
                "Initial note", UUID.randomUUID(), Instant.parse("2026-09-27T04:00:00Z"));
        TreatmentEntry correction = TreatmentEntry.correct(original, "Corrected note", UUID.randomUUID(),
                Instant.parse("2026-09-27T04:10:00Z"));

        assertNotEquals(original.entryId(), correction.entryId());
        assertEquals("Initial note", original.content());
        assertEquals("Corrected note", correction.content());
        assertEquals(TreatmentEntryType.CORRECTION, correction.entryType());
        assertEquals(original.entryId(), correction.correctionOfEntryId());
    }

    @Test
    void correctionCannotTargetAnotherAdmissionOrAnotherCorrection() {
        TreatmentEntry original = TreatmentEntry.create(UUID.randomUUID(), TreatmentEntryType.NOTE,
                "Initial note", UUID.randomUUID(), Instant.parse("2026-09-27T04:00:00Z"));
        TreatmentEntry correction = TreatmentEntry.correct(original, "Correction", UUID.randomUUID(),
                Instant.parse("2026-09-27T04:10:00Z"));

        assertThrows(AdmissionRuleViolationException.class,
                () -> TreatmentEntry.correctForAdmission(UUID.randomUUID(), original, "Wrong admission",
                        UUID.randomUUID(), Instant.parse("2026-09-27T04:10:00Z")));
        assertThrows(AdmissionRuleViolationException.class,
                () -> TreatmentEntry.correct(correction, "Nested correction", UUID.randomUUID(),
                        Instant.parse("2026-09-27T04:20:00Z")));
    }
}
