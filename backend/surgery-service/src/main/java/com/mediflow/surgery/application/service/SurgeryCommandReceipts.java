package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/** Deterministic receipt encoding/fingerprinting shared by transactional command use cases. */
public final class SurgeryCommandReceipts {

    private static final int FORMAT_VERSION = 1;

    private SurgeryCommandReceipts() { }

    public static ClaimResult claim(SurgeryCommandReceiptPort receipts,
                                    SurgeryCommandReceiptPort.Key key,
                                    String fingerprint,
                                    String commandCode,
                                    UUID caseId) {
        SurgeryCommandReceiptPort.Claim claim = receipts.claim(key, fingerprint);
        return switch (claim.state()) {
            case NEW -> new ClaimResult(claim.receiptId(), null);
            case REPLAY -> {
                if (!commandCode.equals(claim.responseCode())) {
                    throw new IllegalStateException("Receipt response code does not match command");
                }
                SurgeryCommandOutcome outcome = decode(claim.response());
                if (!commandCode.equals(outcome.commandCode()) || !caseId.equals(outcome.surgeryCaseId())) {
                    throw new IllegalStateException("Receipt payload identity does not match command");
                }
                yield new ClaimResult(claim.receiptId(), outcome.asReplay());
            }
            case CONFLICT, IN_PROGRESS -> throw new SurgeryRevisionConflictException();
        };
    }

    public static String fingerprint(String... values) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                for (String value : values) {
                    if (value == null) {
                        data.writeInt(-1);
                    } else {
                        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                        data.writeInt(encoded.length);
                        data.write(encoded);
                    }
                }
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return java.util.HexFormat.of().formatHex(digest);
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to fingerprint Surgery command", exception);
        }
    }

    public static void complete(SurgeryCommandReceiptPort receipts, UUID receiptId,
                                SurgeryCommandOutcome outcome) {
        receipts.complete(receiptId, outcome.surgeryCaseId(), outcome.commandCode(),
                encode(outcome), outcome.occurredAt());
    }

    private static byte[] encode(SurgeryCommandOutcome outcome) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                data.writeInt(FORMAT_VERSION);
                data.writeUTF(outcome.commandCode());
                writeUuid(data, outcome.surgeryCaseId());
                data.writeLong(outcome.caseRevision());
                writeUuid(data, outcome.subjectId());
                data.writeLong(outcome.subjectRevision());
                data.writeUTF(outcome.state());
                data.writeLong(outcome.occurredAt().getEpochSecond());
                data.writeInt(outcome.occurredAt().getNano());
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode Surgery command receipt", exception);
        }
    }

    private static SurgeryCommandOutcome decode(byte[] bytes) {
        if (bytes == null) throw new IllegalStateException("Applied receipt has no response payload");
        try (DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (data.readInt() != FORMAT_VERSION) throw new IOException("Unsupported receipt format");
            String command = data.readUTF();
            UUID caseId = readUuid(data);
            long caseRevision = data.readLong();
            UUID subjectId = readUuid(data);
            long subjectRevision = data.readLong();
            String state = data.readUTF();
            Instant occurredAt = Instant.ofEpochSecond(data.readLong(), data.readInt());
            if (data.available() != 0) throw new IOException("Trailing receipt payload bytes");
            return new SurgeryCommandOutcome(command, caseId, caseRevision, subjectId,
                    subjectRevision, state, occurredAt, false);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Stored Surgery command receipt is corrupt", exception);
        }
    }

    private static void writeUuid(DataOutputStream data, UUID value) throws IOException {
        data.writeBoolean(value != null);
        if (value != null) {
            data.writeLong(value.getMostSignificantBits());
            data.writeLong(value.getLeastSignificantBits());
        }
    }

    private static UUID readUuid(DataInputStream data) throws IOException {
        return data.readBoolean() ? new UUID(data.readLong(), data.readLong()) : null;
    }

    public record ClaimResult(UUID receiptId, SurgeryCommandOutcome replay) {
        public ClaimResult {
            if (receiptId == null) throw new IllegalArgumentException("Receipt id is required");
        }

        public boolean isReplay() { return replay != null; }
    }
}
