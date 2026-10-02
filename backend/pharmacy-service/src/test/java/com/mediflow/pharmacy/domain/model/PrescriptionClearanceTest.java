package com.mediflow.pharmacy.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.mediflow.pharmacy.support.ClearanceTestFixtures.*;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;

class PrescriptionClearanceTest {
    @Test
    void requireMatch_exactOutpatient_doesNotRecalculateInvoiceAmount() {
        var grant = grant();
        var prescription = prescription(grant);
        assertThat(grant.amount()).isNotEqualByComparingTo(prescription.getTotalAmount());
        grant.requireMatch(prescription);
    }

    @Test
    void requireMatch_wrongPatientEpisodeOrTarget_rejects() {
        var grant = grant();
        assertThatThrownBy(() -> withPatient(grant, UUID.randomUUID()).requireMatch(prescription(grant)))
                .hasMessageContaining("exact V1");
        assertThatThrownBy(() -> grant.requireMatch(prescription(grant,
                PrescriptionCareContext.v1(CareContext.OUTPATIENT,
                        new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID()), null, "DRUG"))))
                .hasMessageContaining("exact V1");
        assertThatThrownBy(() -> grant.requireMatch(prescription(grant()))).hasMessageContaining("exact V1");
    }

    @Test
    void requireMatch_legacyOrAdmission_rejects() {
        var grant = grant();
        assertThatThrownBy(() -> grant.requireMatch(prescription(grant, PrescriptionCareContext.legacy())))
                .hasMessageContaining("exact V1");
        UUID admissionId = UUID.randomUUID();
        assertThatThrownBy(() -> grant.requireMatch(prescription(grant, PrescriptionCareContext.v1(
                CareContext.ADMISSION, new CareEpisode(CareEpisodeType.ADMISSION, admissionId), admissionId, "DRUG"))))
                .hasMessageContaining("exact V1");
    }

    @Test
    void isValidAt_exactNanosecondExpiry_isExclusive() {
        var grant = grant();
        assertThat(grant.isValidAt(GRANTED_AT.minusNanos(1))).isFalse();
        assertThat(grant.isValidAt(GRANTED_AT)).isTrue();
        assertThat(grant.isValidAt(EXPIRES_AT.minusNanos(1))).isTrue();
        assertThat(grant.isValidAt(EXPIRES_AT)).isFalse();
    }

    @Test
    void construct_badMoneyOrTime_rejectsRatherThanRounding() {
        var grant = grant();
        assertThatThrownBy(() -> new PrescriptionClearance(grant.clearanceId(), grant.invoiceId(), grant.accountId(),
                grant.prescriptionId(), grant.patientId(), grant.episode(), new BigDecimal("0.001"), "VND", "CASH",
                GRANTED_AT, EXPIRES_AT, grant.payloadFingerprint())).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> new PrescriptionClearance(grant.clearanceId(), grant.invoiceId(), grant.accountId(),
                grant.prescriptionId(), grant.patientId(), grant.episode(), grant.amount(), "VND", "CASH",
                GRANTED_AT, GRANTED_AT, grant.payloadFingerprint())).isInstanceOf(IllegalArgumentException.class);
    }
}
