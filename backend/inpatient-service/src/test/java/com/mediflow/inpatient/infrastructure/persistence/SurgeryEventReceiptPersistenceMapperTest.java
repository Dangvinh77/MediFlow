package com.mediflow.inpatient.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.inpatient.domain.model.SurgeryEventReceipt;
import com.mediflow.inpatient.domain.model.enums.ExternalOrderStatus;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.SurgeryEventReceiptJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.mapper.InpatientPersistenceMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SurgeryEventReceiptPersistenceMapperTest {
    @Test
    void receiptRoundTrip_preservesReplayAndSemanticIdentity() {
        Instant receivedAt = Instant.parse("2026-10-07T01:11:00Z");
        Instant appliedAt = receivedAt.plusSeconds(30);
        SurgeryEventReceipt receipt = SurgeryEventReceipt.pending(UUID.randomUUID(),
                "surgery.completed", UUID.randomUUID(), "a".repeat(64), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 5, 1,
                ExternalOrderStatus.COMPLETED, "none", "Surgery completed", receivedAt, receivedAt);
        receipt.markApplied(appliedAt);
        InpatientPersistenceMapper mapper = new InpatientPersistenceMapper();

        SurgeryEventReceiptJpaEntity row = mapper.copy(receipt, new SurgeryEventReceiptJpaEntity());
        SurgeryEventReceipt restored = mapper.toDomain(row);

        assertThat(restored.receiptId()).isEqualTo(receipt.receiptId());
        assertThat(restored.operationId()).isEqualTo(receipt.operationId());
        assertThat(restored.surgeryRequestId()).isEqualTo(receipt.surgeryRequestId());
        assertThat(restored.payloadFingerprint()).isEqualTo(receipt.payloadFingerprint());
        assertThat(restored.targetStatus()).isEqualTo(ExternalOrderStatus.COMPLETED);
        assertThat(restored.appliedAt()).isEqualTo(appliedAt);
    }
}
