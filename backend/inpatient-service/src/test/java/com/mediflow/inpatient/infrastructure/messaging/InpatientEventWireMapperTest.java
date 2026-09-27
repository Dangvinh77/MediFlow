package com.mediflow.inpatient.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.inpatient.application.dto.event.AdmissionDepositRequestedEvent;
import com.mediflow.inpatient.application.dto.event.DomainEventEnvelope;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InpatientEventWireMapperTest {

    private final InpatientEventWireMapper mapper = new InpatientEventWireMapper();

    @Test
    void depositRequestUsesCanonicalSharedEnvelopeAndPayloadNames() {
        UUID eventId = UUID.randomUUID();
        UUID admissionId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-09-27T04:00:00Z");
        var payload = new AdmissionDepositRequestedEvent(admissionId, UUID.randomUUID(), UUID.randomUUID(),
                "ADMISSION", admissionId, "ADMISSION_DEPOSIT", admissionId, "INPATIENT_DEPOSIT",
                new BigDecimal("150000.00"), "Initial admission deposit");
        var event = new DomainEventEnvelope<>(eventId, "admission.deposit.requested", 1,
                occurredAt, "correlation-123", "inpatient-service", payload);

        Map<String, Object> wire = mapper.toWireEnvelope(event);

        assertThat(wire).containsEntry("eventId", eventId)
                .containsEntry("eventType", "admission.deposit.requested")
                .containsEntry("version", 1)
                .containsEntry("occurredAt", occurredAt)
                .containsEntry("correlationId", "correlation-123")
                .containsEntry("producer", "inpatient-service");
        assertThat(wire).doesNotContainKeys("maSuKien", "loaiSuKien", "phienBan", "duLieu");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) wire.get("payload");
        assertThat(body).containsEntry("admissionId", admissionId)
                .containsEntry("careEpisodeType", "ADMISSION")
                .containsEntry("careEpisodeId", admissionId)
                .containsEntry("sourceType", "ADMISSION_DEPOSIT")
                .containsEntry("sourceId", admissionId)
                .containsEntry("priceCode", "INPATIENT_DEPOSIT")
                .containsEntry("suggestedAmount", new BigDecimal("150000.00"));
        assertThat(body).doesNotContainKeys("maDotNoiTru", "loaiTapNoiTru", "soTienGoiY");
    }
}
