package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.PharmacyEventOutboxJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PharmacyEventOutboxJpaRepository;

/** Held producer proposal bytes only; not Billing/Report owner acceptance or live activation. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PrescriptionCareEventWriterAdapter.class, PrescriptionCareEventCodec.class, PrescriptionCareEventWriterPostgresTest.JsonConfig.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PrescriptionCareEventWriterPostgresTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired PrescriptionCareEventWriterAdapter writer;
    @Autowired PrescriptionCareEventCodec codec;
    @Autowired JdbcTemplate jdbc;
    @Autowired PharmacyEventOutboxJpaRepository repository;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() { jdbc.execute("TRUNCATE PHARMACY_EVENT_OUTBOX"); }

    @Test
    void identicalEventIsIdempotentAndTerminalRequiresCreation() throws Exception {
        var created = fixture(EventType.CREATED);
        var filled = fixture(EventType.FILLED);
        assertThatThrownBy(() -> tx(() -> writer.storeHeld(filled))).hasMessageContaining("creation");
        tx(() -> { writer.storeHeld(created); writer.storeHeld(created); writer.storeHeld(filled); });
        assertThat(count()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT delivery_enabled FROM PHARMACY_EVENT_OUTBOX", Boolean.class)).containsExactly(false, false);
    }

    @Test
    void newEventIdForSameLifecycleOrCompetingTerminal_neverOverwritesStoredBytes() throws Exception {
        var created = fixture(EventType.CREATED);
        var filled = fixture(EventType.FILLED);
        tx(() -> { writer.storeHeld(created); writer.storeHeld(filled); });
        String before = jdbc.queryForObject("SELECT payload FROM PHARMACY_EVENT_OUTBOX WHERE event_id = ?", String.class, created.eventId());
        var another = new PrescriptionCareEvent(UUID.randomUUID(), created.eventType(), 1, created.occurredAt(),
                created.correlationId(), created.producer(), created.payload());
        assertThatThrownBy(() -> tx(() -> writer.storeHeld(another))).hasMessageContaining("conflict");
        var cancelled = fixture(EventType.CANCELLED);
        assertThatThrownBy(() -> tx(() -> writer.storeHeld(cancelled))).hasMessageContaining("conflict");
        assertThat(count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT payload FROM PHARMACY_EVENT_OUTBOX WHERE event_id = ?", String.class, created.eventId())).isEqualTo(before);
    }

    @Test
    void eventIdentityCollisionWithLegacyRow_isRejectedWithoutReplacingLegacy() throws Exception {
        var created = fixture(EventType.CREATED);
        var legacy = new PharmacyEventOutboxJpaEntity(created.eventId(), "stock.low", UUID.randomUUID(), "legacy-bytes");
        repository.saveAndFlush(legacy);
        assertThatThrownBy(() -> tx(() -> writer.storeHeld(created))).hasMessageContaining("conflict");
        assertThat(jdbc.queryForObject("SELECT payload FROM PHARMACY_EVENT_OUTBOX WHERE event_id = ?", String.class, created.eventId())).isEqualTo("legacy-bytes");
    }

    @Test
    void operatorReplayDoesNotEnableHeldBytesAndV0StillCanBeClaimed() throws Exception {
        var created = fixture(EventType.CREATED);
        tx(() -> writer.storeHeld(created));
        tx(() -> repository.replay(created.eventId(), Instant.now().minusSeconds(10)));
        var legacy = new PharmacyEventOutboxJpaEntity(UUID.randomUUID(), "prescription.created", UUID.randomUUID(), "legacy");
        legacy.setAvailableAt(Instant.now().minusSeconds(10));
        repository.saveAndFlush(legacy);
        tx(() -> {
            assertThat(repository.findClaimable(Instant.now().plusSeconds(10), Instant.now().minusSeconds(30), 10))
                    .extracting(PharmacyEventOutboxJpaEntity::getEventId).containsExactly(legacy.getEventId());
        });
        assertThat(jdbc.queryForObject("SELECT delivery_enabled FROM PHARMACY_EVENT_OUTBOX WHERE event_id = ?", Boolean.class, created.eventId())).isFalse();
    }

    @Test
    void heldPredecessorBlocksCriticalLegacySuccessorOfSamePrescription() throws Exception {
        var created = fixture(EventType.CREATED);
        tx(() -> writer.storeHeld(created));
        var legacySuccessor = new PharmacyEventOutboxJpaEntity(UUID.randomUUID(), "prescription.filled", created.payload().prescriptionId(), "legacy");
        legacySuccessor.setAvailableAt(Instant.now().minusSeconds(10));
        repository.saveAndFlush(legacySuccessor);
        // Make temporal ordering explicit, without assuming Hibernate and JDBC clocks agree.
        jdbc.update("UPDATE PHARMACY_EVENT_OUTBOX SET created_at = created_at - INTERVAL '1 day' WHERE event_id = ?", created.eventId());
        tx(() -> assertThat(repository.findClaimable(Instant.now().plusSeconds(10), Instant.now().minusSeconds(30), 10)).isEmpty());
    }

    @Test
    void dbConstraintAndLeaseGuardPreventAccidentalActivation() throws Exception {
        var created = fixture(EventType.CREATED);
        tx(() -> writer.storeHeld(created));
        assertThatThrownBy(() -> jdbc.update("UPDATE PHARMACY_EVENT_OUTBOX SET delivery_enabled = true WHERE event_id = ?", created.eventId()))
                .hasMessageContaining("ck_outbox_care_contract");
        jdbc.update("UPDATE PHARMACY_EVENT_OUTBOX SET locked_by = 'replica-a' WHERE event_id = ?", created.eventId());
        tx(() -> assertThat(repository.markPublishedIfOwned(created.eventId(), "replica-a", Instant.now())).isZero());
        tx(() -> assertThat(repository.deletePublishedBefore(Instant.now().plusSeconds(86400))).isZero());
    }

    @Test
    void callerFailureRollsBackAllHeldLifecycleRowsAndRetryPersists() throws Exception {
        var created = fixture(EventType.CREATED);
        var filled = fixture(EventType.FILLED);
        assertThatThrownBy(() -> tx(() -> { writer.storeHeld(created); writer.storeHeld(filled); throw new IllegalStateException("injected caller failure"); }))
                .hasMessageContaining("injected caller failure");
        assertThat(count()).isZero();
        tx(() -> { writer.storeHeld(created); writer.storeHeld(filled); });
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void writerRequiresCallerTransaction() throws Exception {
        var created = fixture(EventType.CREATED);
        assertThatThrownBy(() -> writer.storeHeld(created)).hasMessageContaining("transaction");
        assertThat(count()).isZero();
    }

    private void tx(Runnable action) { new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run()); }
    private int count() { return jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX", Integer.class); }
    private PrescriptionCareEvent fixture(EventType type) throws Exception {
        try (var stream = getClass().getResourceAsStream("/contracts/care-finance-v1/" + type.routingKey() + ".v1.json")) {
            assertThat(stream).isNotNull();
            return codec.decode(type.routingKey(), stream.readAllBytes());
        }
    }
    @TestConfiguration static class JsonConfig { @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); } }
}
