package com.mediflow.inpatient.domain.model;

import com.mediflow.inpatient.domain.exception.AdmissionRuleViolationException;
import com.mediflow.inpatient.domain.model.enums.ClinicalOrderType;
import com.mediflow.inpatient.domain.model.enums.ExternalOrderStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClinicalOrderReferenceTest {

    @Test
    void olderFactCannotRegressCompletedOrder() {
        UUID admissionId = UUID.randomUUID();
        UUID externalId = UUID.randomUUID();
        ClinicalOrderReference reference = ClinicalOrderReference.create(admissionId,
                ClinicalOrderType.LAB_TEST, externalId, ExternalOrderStatus.REQUESTED, "lab", 1);

        reference.applyFact(admissionId, externalId, ExternalOrderStatus.COMPLETED, "result", 4);
        reference.applyFact(admissionId, externalId, ExternalOrderStatus.IN_PROGRESS, "old", 3);

        assertEquals(ExternalOrderStatus.COMPLETED, reference.status());
        assertEquals(4, reference.eventVersion());
        assertEquals("result", reference.summary());
    }

    @Test
    void externalFactMustNameExactAdmissionAndOrder() {
        ClinicalOrderReference reference = ClinicalOrderReference.create(UUID.randomUUID(),
                ClinicalOrderType.PRESCRIPTION, UUID.randomUUID(), ExternalOrderStatus.REQUESTED, null, null);

        assertThrows(AdmissionRuleViolationException.class,
                () -> reference.applyFact(UUID.randomUUID(), reference.externalOrderId(),
                        ExternalOrderStatus.COMPLETED, null, 1));
        assertThrows(AdmissionRuleViolationException.class,
                () -> reference.applyFact(reference.admissionId(), UUID.randomUUID(),
                        ExternalOrderStatus.COMPLETED, null, 1));
    }
}
