package com.mediflow.clinical.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;

import lombok.Getter;

/** One doctor's consultation opinion on a visit. Appended only; it never changes the visit's diagnosis. */
@Getter
public final class ConsultationNote {
    private final UUID noteId;
    private final UUID recordId;
    private final UUID authorStaffId;
    private final String opinion;
    private final Instant createdAt;

    private ConsultationNote(UUID noteId, UUID recordId, UUID authorStaffId, String opinion, Instant createdAt) {
        this.noteId = noteId;
        this.recordId = recordId;
        this.authorStaffId = authorStaffId;
        this.opinion = opinion;
        this.createdAt = createdAt;
    }

    public static ConsultationNote write(UUID recordId, UUID authorStaffId, String opinion, Instant at) {
        if (recordId == null || authorStaffId == null || at == null || opinion == null || opinion.isBlank()
                || opinion.strip().length() > 4000) {
            throw new InvalidClinicalDataException("CONSULTATION_NOTE_INVALID",
                    "A consultation opinion needs 1 to 4000 characters and its author");
        }
        return new ConsultationNote(UUID.randomUUID(), recordId, authorStaffId, opinion.strip(), at);
    }

    public static ConsultationNote restore(UUID noteId, UUID recordId, UUID authorStaffId, String opinion, Instant createdAt) {
        return new ConsultationNote(Objects.requireNonNull(noteId), recordId, authorStaffId, opinion, createdAt);
    }
}
