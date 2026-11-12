package com.mediflow.clinical.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;

import lombok.Getter;

/**
 * A patient-level allergy shown on every visit. Entries are never deleted: a mistaken one is marked
 * {@link AllergyStatus#ENTERED_IN_ERROR} with its reason.
 */
@Getter
public final class PatientAllergy {
    private final UUID allergyId;
    private final UUID patientId;
    private final String substance;
    private final String reaction;
    private final AllergySeverity severity;
    private AllergyStatus status;
    private final UUID recordedByStaffId;
    private final Instant recordedAt;
    private final UUID sourceRecordId;
    private String statusReason;
    private UUID statusChangedByStaffId;
    private Instant statusChangedAt;

    private PatientAllergy(UUID allergyId, UUID patientId, String substance, String reaction, AllergySeverity severity,
            AllergyStatus status, UUID recordedByStaffId, Instant recordedAt, UUID sourceRecordId, String statusReason,
            UUID statusChangedByStaffId, Instant statusChangedAt) {
        this.allergyId = allergyId;
        this.patientId = patientId;
        this.substance = substance;
        this.reaction = reaction;
        this.severity = severity;
        this.status = status;
        this.recordedByStaffId = recordedByStaffId;
        this.recordedAt = recordedAt;
        this.sourceRecordId = sourceRecordId;
        this.statusReason = statusReason;
        this.statusChangedByStaffId = statusChangedByStaffId;
        this.statusChangedAt = statusChangedAt;
    }

    public static PatientAllergy record(UUID patientId, String substance, String reaction, AllergySeverity severity,
            UUID sourceRecordId, UUID staffId, Instant at) {
        if (patientId == null || staffId == null || at == null || substance == null || substance.isBlank()
                || substance.strip().length() > 200 || reaction != null && reaction.strip().length() > 500) {
            throw invalid("An allergy needs a substance up to 200 characters and a reaction up to 500 characters");
        }
        String note = reaction == null || reaction.isBlank() ? null : reaction.strip();
        return new PatientAllergy(UUID.randomUUID(), patientId, substance.strip(), note,
                severity == null ? AllergySeverity.UNKNOWN : severity, AllergyStatus.ACTIVE, staffId, at, sourceRecordId,
                null, null, null);
    }

    public static PatientAllergy restore(UUID allergyId, UUID patientId, String substance, String reaction,
            AllergySeverity severity, AllergyStatus status, UUID recordedByStaffId, Instant recordedAt, UUID sourceRecordId,
            String statusReason, UUID statusChangedByStaffId, Instant statusChangedAt) {
        return new PatientAllergy(Objects.requireNonNull(allergyId), patientId, substance, reaction, severity,
                Objects.requireNonNull(status), recordedByStaffId, recordedAt, sourceRecordId, statusReason,
                statusChangedByStaffId, statusChangedAt);
    }

    /** Case-insensitive, whitespace-collapsed key used to refuse a second active entry for one substance. */
    public static String key(String substance) {
        return substance == null ? "" : substance.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    public String substanceKey() {
        return key(substance);
    }

    public boolean isActive() {
        return status == AllergyStatus.ACTIVE;
    }

    public void changeStatus(AllergyStatus next, String reason, UUID staffId, Instant at) {
        if (status != AllergyStatus.ACTIVE || next == null || next == AllergyStatus.ACTIVE || staffId == null || at == null
                || reason == null || reason.isBlank() || reason.strip().length() > 500) {
            throw invalid("Only an active allergy can be resolved or marked entered in error, with a reason up to 500 characters");
        }
        status = next;
        statusReason = reason.strip();
        statusChangedByStaffId = staffId;
        statusChangedAt = at;
    }

    private static InvalidClinicalDataException invalid(String message) {
        return new InvalidClinicalDataException("ALLERGY_INVALID", message);
    }
}
