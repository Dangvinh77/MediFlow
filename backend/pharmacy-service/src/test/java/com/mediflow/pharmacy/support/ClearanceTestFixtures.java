package com.mediflow.pharmacy.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.mediflow.pharmacy.domain.model.CareEpisode;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionCareContext;
import com.mediflow.pharmacy.domain.model.PrescriptionClearance;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;

/** Consumer-local typed examples, not approved producer fixture bytes. */
public final class ClearanceTestFixtures {
    public static final Instant GRANTED_AT = Instant.parse("2026-10-01T03:00:00.123456789Z");
    public static final Instant EXPIRES_AT = GRANTED_AT.plusSeconds(600);

    private ClearanceTestFixtures() { }

    public static PrescriptionClearance grant() {
        return new PrescriptionClearance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID()),
                new BigDecimal("200.00"), "VND", "CASH", GRANTED_AT, EXPIRES_AT, "a".repeat(64));
    }

    public static Prescription prescription(PrescriptionClearance grant) {
        return prescription(grant, PrescriptionCareContext.v1(CareContext.OUTPATIENT, grant.episode(), null, "DRUG"));
    }

    public static Prescription prescription(PrescriptionClearance grant, PrescriptionCareContext context) {
        return Prescription.restore(grant.prescriptionId(), UUID.randomUUID(), grant.patientId(),
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 10, 1), new BigDecimal("100.00"),
                List.of(PrescriptionLine.create(UUID.randomUUID(), 1, new BigDecimal("100.00"), "Daily")),
                PrescriptionStatus.ACTIVE, null, null, null, GRANTED_AT, GRANTED_AT, context);
    }

    public static PrescriptionClearance withPatient(PrescriptionClearance grant, UUID patientId) {
        return new PrescriptionClearance(grant.clearanceId(), grant.invoiceId(), grant.accountId(),
                grant.prescriptionId(), patientId, grant.episode(), grant.amount(), grant.currency(),
                grant.paymentMethod(), grant.grantedAt(), grant.expiresAt(), grant.payloadFingerprint());
    }
}
