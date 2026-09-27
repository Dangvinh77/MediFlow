package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import com.mediflow.inpatient.domain.model.enums.BedStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BedTest {

    @Test
    void onlyAnActiveAvailableBedCanBeAssigned() {
        Bed bed = Bed.create(UUID.randomUUID(), UUID.randomUUID(), "W1", "R1", "B1", "GENERAL");

        assertEquals(BedStatus.OCCUPIED, bed.assign().status());
        assertThrows(AdmissionRuleViolationException.class, bed::assign);
        assertEquals(BedStatus.AVAILABLE, bed.release().status());
    }

    @Test
    void occupiedBedCannotBeDeactivated() {
        Bed bed = Bed.create(UUID.randomUUID(), UUID.randomUUID(), "W1", "R1", "B1", "GENERAL").assign();

        assertThrows(AdmissionRuleViolationException.class,
                () -> bed.update("GENERAL", BedStatus.OUT_OF_SERVICE, false, true));
    }
}
