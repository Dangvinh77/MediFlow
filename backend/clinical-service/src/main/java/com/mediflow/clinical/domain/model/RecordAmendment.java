package com.mediflow.clinical.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

import com.mediflow.clinical.domain.exception.InvalidClinicalDataException;
import com.mediflow.common.exception.ForbiddenOperationException;

import lombok.Getter;

/**
 * A proposed correction of one section of a finished visit. The visit itself never changes: readers see the original
 * next to each approved amendment.
 */
@Getter
public final class RecordAmendment {
    private final UUID amendmentId;
    private final UUID recordId;
    private final AmendmentSection section;
    private final String originalText;
    private final String amendedText;
    private final String reason;
    private final UUID authorStaffId;
    private final UUID assignedByStaffId;
    private RecordAmendmentStatus status;
    private final Instant requestedAt;
    private UUID decidedByStaffId;
    private String decisionNote;
    private Instant decidedAt;
    private String contentHash;

    private RecordAmendment(UUID amendmentId, UUID recordId, AmendmentSection section, String originalText,
            String amendedText, String reason, UUID authorStaffId, UUID assignedByStaffId, RecordAmendmentStatus status,
            Instant requestedAt, UUID decidedByStaffId, String decisionNote, Instant decidedAt, String contentHash) {
        this.amendmentId = amendmentId;
        this.recordId = recordId;
        this.section = section;
        this.originalText = originalText;
        this.amendedText = amendedText;
        this.reason = reason;
        this.authorStaffId = authorStaffId;
        this.assignedByStaffId = assignedByStaffId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.decidedByStaffId = decidedByStaffId;
        this.decisionNote = decisionNote;
        this.decidedAt = decidedAt;
        this.contentHash = contentHash;
    }

    public static RecordAmendment request(UUID recordId, AmendmentSection section, String originalText,
            String amendedText, String reason, UUID authorStaffId, UUID assignedByStaffId, Instant at) {
        String original = originalText == null ? "" : originalText;
        if (recordId == null || section == null || authorStaffId == null || at == null || !text(amendedText, 4000)
                || !text(reason, 1000) || amendedText.strip().equals(original.strip())) {
            throw invalid("An amendment needs a section, new text up to 4000 characters that differs from the original "
                    + "and a reason up to 1000 characters");
        }
        return new RecordAmendment(UUID.randomUUID(), recordId, section, original, amendedText.strip(), reason.strip(),
                authorStaffId, assignedByStaffId, RecordAmendmentStatus.PENDING, at, null, null, null, null);
    }

    public static RecordAmendment restore(UUID amendmentId, UUID recordId, AmendmentSection section, String originalText,
            String amendedText, String reason, UUID authorStaffId, UUID assignedByStaffId, RecordAmendmentStatus status,
            Instant requestedAt, UUID decidedByStaffId, String decisionNote, Instant decidedAt, String contentHash) {
        return new RecordAmendment(Objects.requireNonNull(amendmentId), recordId, section, originalText, amendedText, reason,
                authorStaffId, assignedByStaffId, Objects.requireNonNull(status), requestedAt, decidedByStaffId,
                decisionNote, decidedAt, contentHash);
    }

    /** Refuses a decision that is already taken or that the author would take on their own amendment. */
    public void requireDecidableBy(UUID staffId) {
        if (status != RecordAmendmentStatus.PENDING) {
            throw new InvalidClinicalDataException("AMENDMENT_ALREADY_DECIDED", "This amendment is already decided");
        }
        if (staffId == null || staffId.equals(authorStaffId)) {
            throw new ForbiddenOperationException("AMENDMENT_SELF_DECISION", "The author cannot decide their own amendment");
        }
    }

    public void approve(UUID staffId, String note, Instant at) {
        requireDecidableBy(staffId);
        if (at == null || note != null && note.strip().length() > 1000) {
            throw invalid("A decision note is at most 1000 characters");
        }
        decide(RecordAmendmentStatus.APPROVED, staffId, note == null || note.isBlank() ? null : note.strip(), at);
        contentHash = contentHash();
    }

    public void reject(UUID staffId, String note, Instant at) {
        requireDecidableBy(staffId);
        if (at == null || !text(note, 1000)) {
            throw invalid("A rejection needs a note up to 1000 characters");
        }
        decide(RecordAmendmentStatus.REJECTED, staffId, note.strip(), at);
    }

    /** SHA-256 hex of section, original, amended text and reason; stored only once approved. */
    public String contentHash() {
        String content = section.name() + "\n" + originalText + "\n" + amendedText + "\n" + reason;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void decide(RecordAmendmentStatus next, UUID staffId, String note, Instant at) {
        status = next;
        decidedByStaffId = staffId;
        decisionNote = note;
        decidedAt = at;
    }

    private static boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.strip().length() <= max;
    }

    private static InvalidClinicalDataException invalid(String message) {
        return new InvalidClinicalDataException("AMENDMENT_INVALID", message);
    }
}
