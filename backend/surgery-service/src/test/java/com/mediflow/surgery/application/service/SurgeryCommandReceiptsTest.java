package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SurgeryCommandReceiptsTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-09-29T02:00:00Z");

    @Test
    void fingerprint_valuesWithDifferentBoundaries_producesDifferentDigests() {
        assertThat(SurgeryCommandReceipts.fingerprint("ab", "c"))
                .isNotEqualTo(SurgeryCommandReceipts.fingerprint("a", "bc"));
        assertThat(SurgeryCommandReceipts.fingerprint((String) null))
                .isNotEqualTo(SurgeryCommandReceipts.fingerprint(""));
    }

    @Test
    void claim_appliedReceipt_replaysTheOriginalOutcome() {
        RecordingReceiptPort receipts = new RecordingReceiptPort();
        UUID receiptId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        SurgeryCommandOutcome original = new SurgeryCommandOutcome(
                "BEGIN_PREOP", caseId, 2, null, 0, "PREOP_IN_PROGRESS", OCCURRED_AT, false);
        SurgeryCommandReceipts.complete(receipts, receiptId, original);
        receipts.nextClaim = new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.REPLAY, receiptId, "BEGIN_PREOP", receipts.response);

        SurgeryCommandReceipts.ClaimResult claim = SurgeryCommandReceipts.claim(
                receipts, key(), "a".repeat(64), "BEGIN_PREOP", caseId);

        assertThat(claim.isReplay()).isTrue();
        assertThat(claim.replay()).isEqualTo(original.asReplay());
        assertThat(receipts.completedCaseId).isEqualTo(caseId);
        assertThat(receipts.completedAt).isEqualTo(OCCURRED_AT);
    }

    @Test
    void claim_replayForDifferentCase_rejectsReceiptIdentityMismatch() {
        RecordingReceiptPort receipts = new RecordingReceiptPort();
        UUID receiptId = UUID.randomUUID();
        UUID originalCaseId = UUID.randomUUID();
        SurgeryCommandReceipts.complete(receipts, receiptId, new SurgeryCommandOutcome(
                "BEGIN_PREOP", originalCaseId, 2, null, 0, "PREOP_IN_PROGRESS", OCCURRED_AT, false));
        receipts.nextClaim = new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.REPLAY, receiptId, "BEGIN_PREOP", receipts.response);

        assertThatThrownBy(() -> SurgeryCommandReceipts.claim(
                receipts, key(), "a".repeat(64), "BEGIN_PREOP", UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Receipt payload identity does not match command");
    }

    private static SurgeryCommandReceiptPort.Key key() {
        return new SurgeryCommandReceiptPort.Key("actor", "BEGIN_PREOP:case", "request-1");
    }

    private static final class RecordingReceiptPort implements SurgeryCommandReceiptPort {
        private Claim nextClaim = new Claim(State.NEW, UUID.randomUUID(), null, null);
        private byte[] response;
        private UUID completedCaseId;
        private Instant completedAt;

        @Override
        public Claim claim(Key key, String fingerprint) {
            return nextClaim;
        }

        @Override
        public void complete(UUID receiptId, UUID caseId, String responseCode,
                             byte[] response, Instant at) {
            this.response = response.clone();
            this.completedCaseId = caseId;
            this.completedAt = at;
        }
    }
}
