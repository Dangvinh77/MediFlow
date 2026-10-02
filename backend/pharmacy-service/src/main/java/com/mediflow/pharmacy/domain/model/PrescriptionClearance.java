package com.mediflow.pharmacy.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.pharmacy.domain.exception.DispenseAuthorizationException;
import com.mediflow.pharmacy.domain.model.enums.CareContext;
import com.mediflow.pharmacy.domain.model.enums.CareContractVersion;
import com.mediflow.pharmacy.domain.model.enums.CareEpisodeType;

/** Immutable Billing authorization, not a payment receipt or a financial calculation. */
public record PrescriptionClearance(
        UUID clearanceId, UUID invoiceId, UUID accountId, UUID prescriptionId, UUID patientId,
        CareEpisode episode, BigDecimal amount, String currency, String paymentMethod,
        Instant grantedAt, Instant expiresAt, String payloadFingerprint) {

    public PrescriptionClearance {
        if (clearanceId == null || invoiceId == null || accountId == null || prescriptionId == null
                || patientId == null || episode == null || episode.type() != CareEpisodeType.OUTPATIENT_VISIT
                || amount == null || !"VND".equals(currency) || paymentMethod == null
                || !paymentMethod.matches("[A-Z][A-Z0-9_]{0,31}") || grantedAt == null
                || (expiresAt != null && !expiresAt.isAfter(grantedAt))
                || payloadFingerprint == null || !payloadFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid prescription clearance snapshot");
        }
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (amount.signum() < 0 || amount.precision() > 19) {
            throw new IllegalArgumentException("Invalid clearance amount");
        }
    }

    public boolean matches(Prescription prescription) {
        return prescription != null && prescriptionId.equals(prescription.getPrescriptionId())
                && patientId.equals(prescription.getPatientId())
                && prescription.getCareContext().contractVersion() == CareContractVersion.V1
                && prescription.getCareContext().careContext() == CareContext.OUTPATIENT
                && episode.equals(prescription.getCareContext().episode());
    }

    public void requireMatch(Prescription prescription) {
        if (!matches(prescription)) {
            throw new DispenseAuthorizationException("PHARMACY_CLEARANCE_TARGET_MISMATCH",
                    "Clearance must match the exact V1 outpatient prescription, patient and episode");
        }
    }

    /** Expiry is exclusive; an expired grant is retained for audit, never treated as payment. */
    public boolean isValidAt(Instant at) {
        return at != null && !at.isBefore(grantedAt) && (expiresAt == null || at.isBefore(expiresAt));
    }
}
