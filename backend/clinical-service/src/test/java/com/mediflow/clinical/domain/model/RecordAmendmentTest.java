package com.mediflow.clinical.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.common.exception.ForbiddenOperationException;

class RecordAmendmentTest {
    static final Instant AT = Instant.parse("2026-11-10T03:00:00Z");
    final UUID record = UUID.randomUUID();
    final UUID author = UUID.randomUUID();
    final UUID head = UUID.randomUUID();

    private RecordAmendment pending() {
        return RecordAmendment.request(record, AmendmentSection.SYMPTOMS, "Sốt", "Sốt cao 39 độ", "Ghi thiếu", author, null, AT);
    }

    @Test void reasonAndTextRequired() {
        assertThatThrownBy(() -> RecordAmendment.request(record, AmendmentSection.SYMPTOMS, "Sốt", " ", "Ghi thiếu", author, null, AT))
                .extracting("code").isEqualTo("AMENDMENT_INVALID");
        assertThatThrownBy(() -> RecordAmendment.request(record, AmendmentSection.SYMPTOMS, "Sốt", "x".repeat(4001), "Ghi thiếu", author, null, AT))
                .extracting("code").isEqualTo("AMENDMENT_INVALID");
        assertThatThrownBy(() -> RecordAmendment.request(record, AmendmentSection.SYMPTOMS, "Sốt", "Sốt cao", "", author, null, AT))
                .extracting("code").isEqualTo("AMENDMENT_INVALID");
        assertThatThrownBy(() -> RecordAmendment.request(record, null, "Sốt", "Sốt cao", "Ghi thiếu", author, null, AT))
                .extracting("code").isEqualTo("AMENDMENT_INVALID");
        assertThatThrownBy(() -> RecordAmendment.request(record, AmendmentSection.SYMPTOMS, "Sốt", "Sốt", "Ghi thiếu", author, null, AT))
                .extracting("code").isEqualTo("AMENDMENT_INVALID");
    }

    @Test void authorCannotDecideOwnAmendment() {
        RecordAmendment amendment = pending();
        assertThatThrownBy(() -> amendment.approve(author, null, AT)).isInstanceOf(ForbiddenOperationException.class)
                .extracting("code").isEqualTo("AMENDMENT_SELF_DECISION");
        assertThat(amendment.getStatus()).isEqualTo(RecordAmendmentStatus.PENDING);
    }

    @Test void approvalStoresHashActorAndTime() throws Exception {
        RecordAmendment amendment = pending();
        amendment.approve(head, "Đồng ý", AT);
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("SYMPTOMS\nSốt\nSốt cao 39 độ\nGhi thiếu".getBytes(StandardCharsets.UTF_8)));
        assertThat(amendment.getContentHash()).isEqualTo(expected).isEqualTo(amendment.contentHash());
        assertThat(amendment.getDecidedByStaffId()).isEqualTo(head);
        assertThat(amendment.getDecidedAt()).isEqualTo(AT);
        assertThat(amendment.getStatus()).isEqualTo(RecordAmendmentStatus.APPROVED);
    }

    @Test void rejectionNeedsNoteAndKeepsNoHash() {
        RecordAmendment amendment = pending();
        assertThatThrownBy(() -> amendment.reject(head, " ", AT)).extracting("code").isEqualTo("AMENDMENT_INVALID");
        amendment.reject(head, "Không đủ căn cứ", AT);
        assertThat(amendment.getStatus()).isEqualTo(RecordAmendmentStatus.REJECTED);
        assertThat(amendment.getContentHash()).isNull();
    }

    @Test void decisionIsFinal() {
        RecordAmendment amendment = pending();
        amendment.approve(head, null, AT);
        assertThatThrownBy(() -> amendment.reject(head, "Đổi ý", AT)).extracting("code").isEqualTo("AMENDMENT_ALREADY_DECIDED");
        assertThatThrownBy(() -> amendment.approve(head, null, AT)).extracting("code").isEqualTo("AMENDMENT_ALREADY_DECIDED");
    }
}
