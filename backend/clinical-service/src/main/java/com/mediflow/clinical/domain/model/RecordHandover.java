package com.mediflow.clinical.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.common.exception.ForbiddenOperationException;

import lombok.Getter;

/** A request to move an open visit to another doctor of the same department; it takes effect when accepted. */
@Getter
public final class RecordHandover {
    private final UUID handoverId;
    private final UUID recordId;
    private final UUID fromDoctorId;
    private final UUID toDoctorId;
    private final String reason;
    private final String pendingItems;
    private RecordHandoverStatus status;
    private final Instant requestedAt;
    private Instant acceptedAt;

    private RecordHandover(UUID handoverId, UUID recordId, UUID fromDoctorId, UUID toDoctorId, String reason,
            String pendingItems, RecordHandoverStatus status, Instant requestedAt, Instant acceptedAt) {
        this.handoverId = handoverId;
        this.recordId = recordId;
        this.fromDoctorId = fromDoctorId;
        this.toDoctorId = toDoctorId;
        this.reason = reason;
        this.pendingItems = pendingItems;
        this.status = status;
        this.requestedAt = requestedAt;
        this.acceptedAt = acceptedAt;
    }

    public static RecordHandover request(UUID recordId, UUID fromDoctorId, UUID toDoctorId, String reason,
            String pendingItems, Instant at) {
        if (recordId == null || fromDoctorId == null || toDoctorId == null || at == null
                || fromDoctorId.equals(toDoctorId) || !text(reason, 500) || !text(pendingItems, 2000)) {
            throw invalid("A handover needs another doctor, a reason up to 500 and pending items up to 2000 characters");
        }
        return new RecordHandover(UUID.randomUUID(), recordId, fromDoctorId, toDoctorId, reason.strip(),
                pendingItems.strip(), RecordHandoverStatus.PENDING, at, null);
    }

    public static RecordHandover restore(UUID handoverId, UUID recordId, UUID fromDoctorId, UUID toDoctorId,
            String reason, String pendingItems, RecordHandoverStatus status, Instant requestedAt, Instant acceptedAt) {
        return new RecordHandover(Objects.requireNonNull(handoverId), recordId, fromDoctorId, toDoctorId, reason,
                pendingItems, Objects.requireNonNull(status), requestedAt, acceptedAt);
    }

    public void accept(UUID actorStaffId, Instant at) {
        if (status != RecordHandoverStatus.PENDING || at == null) {
            throw invalid("Only a pending handover can be accepted");
        }
        if (!toDoctorId.equals(actorStaffId)) {
            throw new ForbiddenOperationException("CLINICAL_DOCTOR_NOT_ASSIGNED", "Only the receiving doctor may accept");
        }
        status = RecordHandoverStatus.ACCEPTED;
        acceptedAt = at;
    }

    private static boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.strip().length() <= max;
    }

    private static InvalidClinicalDataException invalid(String message) {
        return new InvalidClinicalDataException("HANDOVER_INVALID", message);
    }
}
