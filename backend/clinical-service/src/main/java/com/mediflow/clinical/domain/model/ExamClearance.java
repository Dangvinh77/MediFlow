package com.mediflow.clinical.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExamClearance {
    private final UUID clearanceId;
    private final UUID eventId;
    private final UUID invoiceId;
    private final UUID accountId;
    private final UUID appointmentId;
    private final UUID recordId;
    private final UUID patientId;
    private final CareEpisodeType careEpisodeType;
    private final UUID careEpisodeId;
    private final BigDecimal amount;
    private final String currency;
    private final Instant expiresAt;
    private final boolean emergencyOverride;
    private final Instant grantedAt;

    public static ExamClearance grant(UUID eventId, UUID clearanceId, UUID invoiceId, UUID accountId,
                                      UUID appointmentId, UUID recordId, UUID patientId,
                                      CareEpisodeType careEpisodeType, UUID careEpisodeId,
                                      ClearancePurpose purpose, BigDecimal amount, String currency,
                                      Instant expiresAt, boolean emergencyOverride, Instant grantedAt) {
        if (eventId == null || clearanceId == null || invoiceId == null || accountId == null || patientId == null
                || careEpisodeType == null || careEpisodeId == null || purpose != ClearancePurpose.EXAM
                || amount == null || amount.signum() < 0 || currency == null
                || !currency.matches("[A-Z]{3}") || grantedAt == null) {
            throw invalidClearance();
        }
        boolean appointmentTarget = appointmentId != null && recordId == null;
        boolean walkInTarget = appointmentId == null && recordId != null;
        UUID expectedEpisode = appointmentTarget ? appointmentId : recordId;
        if ((!appointmentTarget && !walkInTarget)
                || careEpisodeType != CareEpisodeType.OUTPATIENT_VISIT
                || !expectedEpisode.equals(careEpisodeId)) {
            throw invalidClearance();
        }
        return new ExamClearance(clearanceId, eventId, invoiceId, accountId, appointmentId, recordId, patientId,
                careEpisodeType, careEpisodeId, amount, currency, expiresAt, emergencyOverride, grantedAt);
    }

    public static ExamClearance restore(UUID clearanceId, UUID eventId, UUID invoiceId, UUID accountId,
                                        UUID appointmentId, UUID recordId, UUID patientId,
                                        CareEpisodeType careEpisodeType, UUID careEpisodeId, BigDecimal amount,
                                        String currency, Instant expiresAt, boolean emergencyOverride,
                                        Instant grantedAt) {
        return new ExamClearance(clearanceId, eventId, invoiceId, accountId, appointmentId, recordId,
                patientId, careEpisodeType, careEpisodeId, amount, currency, expiresAt, emergencyOverride, grantedAt);
    }
    public boolean matchesAppointment(Appointment appointment) {
        return appointment != null && appointmentId != null
                && appointmentId.equals(appointment.getAppointmentId())
                && patientId.equals(appointment.getPatientId())
                && careEpisodeType == CareEpisodeType.OUTPATIENT_VISIT
                && careEpisodeId.equals(appointment.getAppointmentId());
    }

    public boolean matchesRecord(MedicalRecord record) {
        return record != null && appointmentId == null && recordId != null
                && recordId.equals(record.getRecordId()) && patientId.equals(record.getPatientId())
                && careEpisodeType == CareEpisodeType.OUTPATIENT_VISIT
                && careEpisodeId.equals(record.getRecordId());
    }

    public boolean isValidAt(Instant at) {
        return at != null && (expiresAt == null || expiresAt.isAfter(at));
    }

    private static InvalidClinicalDataException invalidClearance() {
        return new InvalidClinicalDataException("CLINICAL_CLEARANCE_TARGET_MISMATCH",
                "Financial clearance is malformed or does not identify one exact EXAM target");
    }
}
