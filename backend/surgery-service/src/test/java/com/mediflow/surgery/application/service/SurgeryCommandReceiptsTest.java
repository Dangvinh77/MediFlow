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

    @Test void reasonsAndNanosecondsSurviveVersionTwoReplay() {
        var receipts=new RecordingReceiptPort(); var id=UUID.randomUUID();
        var original=new SurgeryCommandOutcome("EVALUATE_READINESS",UUID.randomUUID(),1,UUID.randomUUID(),0,
                "NOT_READY",OCCURRED_AT.plusNanos(123456789),false,java.util.List.of("FINANCIAL_CLEARANCE_INVALID"));
        SurgeryCommandReceipts.complete(receipts,id,original);
        var replay=SurgeryCommandReceipts.resolve(new SurgeryCommandReceiptPort.Claim(SurgeryCommandReceiptPort.State.REPLAY,
                id,original.commandCode(),receipts.response),original.commandCode(),original.surgeryCaseId()).replay();
        assertThat(replay).isEqualTo(original.asReplay());
    }

    @Test void originalVersionOneReceiptStillReplaysWithoutRewrite() {
        var receipts=new RecordingReceiptPort(); var id=UUID.randomUUID();
        var original=new SurgeryCommandOutcome("BEGIN_PREOP",UUID.randomUUID(),1,null,0,"PREOP_IN_PROGRESS",OCCURRED_AT,false);
        SurgeryCommandReceipts.complete(receipts,id,original);
        byte[] legacy=receipts.response.clone();
        assertThat(java.nio.ByteBuffer.wrap(legacy).getInt()).isOne();
        var replay=SurgeryCommandReceipts.resolve(new SurgeryCommandReceiptPort.Claim(SurgeryCommandReceiptPort.State.REPLAY,
                id,original.commandCode(),legacy),original.commandCode(),original.surgeryCaseId()).replay();
        assertThat(replay).isEqualTo(original.asReplay());
        assertThat(java.nio.ByteBuffer.wrap(legacy).getInt()).isOne();
    }

    @Test void corruptReasonCountOrTrailingPayloadCannotBeReplayed() {
        var receipts=new RecordingReceiptPort(); var id=UUID.randomUUID(); var caseId=UUID.randomUUID();
        SurgeryCommandReceipts.complete(receipts,id,new SurgeryCommandOutcome("BEGIN_PREOP",caseId,1,null,0,"PREOP_IN_PROGRESS",OCCURRED_AT,false));
        byte[] emptyV2=java.util.Arrays.copyOf(receipts.response,receipts.response.length+Integer.BYTES);
        java.nio.ByteBuffer.wrap(emptyV2).putInt(2);
        byte[] badCount=emptyV2.clone(); java.nio.ByteBuffer.wrap(badCount).putInt(badCount.length-4,33);
        byte[] trailing=java.util.Arrays.copyOf(emptyV2,emptyV2.length+1);
        for(byte[] invalid:new byte[][]{badCount,trailing}) assertThatThrownBy(() -> SurgeryCommandReceipts.resolve(
                new SurgeryCommandReceiptPort.Claim(SurgeryCommandReceiptPort.State.REPLAY,id,"BEGIN_PREOP",invalid),"BEGIN_PREOP",caseId))
                .isInstanceOf(IllegalStateException.class).hasMessage("Stored Surgery command receipt is corrupt");
    }

    private static final class RecordingReceiptPort implements SurgeryCommandReceiptPort {
        public java.util.Optional<Claim> find(Key key, String fingerprint) { return java.util.Optional.empty(); }
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
