package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.port.out.LegacyClinicalEventMetadata;
import com.mediflow.clinical.infrastructure.persistence.adapter.ClinicalOutboxPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ClinicalOutboxEventJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalOutboxJpaRepository;

class ClinicalOutboxPersistenceAdapterTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID APPOINTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PATIENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID DOCTOR_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID DEPARTMENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-14T10:53:00Z");

    private final ClinicalOutboxJpaRepository repository = mock(ClinicalOutboxJpaRepository.class);
    private final ClinicalOutboxPersistenceAdapter adapter =
            new ClinicalOutboxPersistenceAdapter(repository, new ObjectMapper());

    @Test
    void appendLegacyFlatEventStoresOriginalBytesAndMetadata() {
        LegacyClinicalEventMetadata metadata = metadata();
        byte[] wireBytes = wireBytes();

        adapter.appendLegacyEvent(metadata, wireBytes);
        wireBytes[0] = (byte) '[';

        ArgumentCaptor<ClinicalOutboxEventJpaEntity> saved =
                ArgumentCaptor.forClass(ClinicalOutboxEventJpaEntity.class);
        verify(repository).save(saved.capture());
        ClinicalOutboxEventJpaEntity row = saved.getValue();
        assertThat(row.getEventId()).isEqualTo(EVENT_ID);
        assertThat(row.getAggregateType()).isEqualTo("APPOINTMENT");
        assertThat(row.getAggregateId()).isEqualTo(APPOINTMENT_ID);
        assertThat(row.getEventType()).isEqualTo("appointment.created");
        assertThat(row.getEventVersion()).isZero();
        assertThat(row.getCorrelationId()).isEqualTo("correlation-test");
        assertThat(row.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(row.getPayloadBytes()).containsExactly(wireBytes());
        assertThat(row.getPayload().path("eventId").asText()).isEqualTo(EVENT_ID.toString());
        assertThat(row.getPayload().path("appointmentTime").asText()).isEqualTo("09:30");
        assertThat(row.getPayload().has("payload")).isFalse();
    }

    @Test
    void appendLegacyFlatEventRejectsBytesThatWouldChangeTheFlatContract() {
        byte[] wrappedPayload = ("{\"eventId\":\"" + EVENT_ID + "\",\"occurredAt\":\"" + OCCURRED_AT
                + "\",\"correlationId\":\"correlation-test\",\"payload\":{}}")
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.appendLegacyEvent(metadata(), wrappedPayload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("flat event metadata");
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void appendLegacyFlatEventRejectsMissingCorrelationEvidence() {
        byte[] wireBytes = "{}".getBytes(StandardCharsets.UTF_8);
        LegacyClinicalEventMetadata metadata = new LegacyClinicalEventMetadata(
                EVENT_ID, "appointment.created", null, APPOINTMENT_ID, OCCURRED_AT);

        assertThatThrownBy(() -> adapter.appendLegacyEvent(metadata, wireBytes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId is required");
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    private static LegacyClinicalEventMetadata metadata() {
        return new LegacyClinicalEventMetadata(EVENT_ID, "appointment.created", "correlation-test", APPOINTMENT_ID,
                OCCURRED_AT);
    }

    private static byte[] wireBytes() {
        return ("{\"eventId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"occurredAt\":1791975180.000000000,\"correlationId\":\"correlation-test\","
                + "\"appointmentId\":\"22222222-2222-2222-2222-222222222222\","
                + "\"patientId\":\"33333333-3333-3333-3333-333333333333\","
                + "\"doctorId\":\"44444444-4444-4444-4444-444444444444\","
                + "\"departmentId\":\"55555555-5555-5555-5555-555555555555\","
                + "\"appointmentDate\":[2026,10,15],\"appointmentTime\":\"09:30\"}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
