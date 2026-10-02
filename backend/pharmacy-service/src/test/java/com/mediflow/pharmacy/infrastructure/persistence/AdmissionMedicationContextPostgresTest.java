package com.mediflow.pharmacy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.pharmacy.application.dto.command.AdmissionLifecycleCommand;
import com.mediflow.pharmacy.application.service.AdmissionLifecycleApplicationService;
import com.mediflow.pharmacy.domain.model.AdmissionLifecycleFact;
import com.mediflow.pharmacy.domain.model.AdmissionMedicationContext;
import com.mediflow.pharmacy.infrastructure.persistence.adapter.AdmissionMedicationContextPersistenceAdapter;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdmissionMedicationContextPersistenceAdapter.class, AdmissionLifecycleApplicationService.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdmissionMedicationContextPostgresTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired AdmissionLifecycleApplicationService service;
    @Autowired AdmissionMedicationContextPersistenceAdapter contexts;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    private final UUID admissionId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final Instant startedAt = Instant.parse("2026-09-28T02:10:00.123456789Z");

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE admission_medication_event, admission_medication_context");
    }

    @Test
    void closeBeforeStart_survivesReloadAndPreservesExactSourceInstant() {
        service.project(close());
        service.project(start());
        var stored = reload();
        assertThat(stored.startedAt()).isEqualTo(startedAt);
        assertThat(stored.closedAt()).isNotNull();
        assertThat(stored.departmentId()).isEqualTo(departmentId);
        assertThat(stored.version()).isEqualTo(2);
        assertThatThrownBy(() -> stored.requireActive(patientId, departmentId)).hasMessageContaining("not active");
    }

    @Test
    void sameEventDifferentPayloadAndWrongPatient_rollBackClaimsAndState() {
        var command = start();
        service.project(command);
        assertThatThrownBy(() -> service.project(new AdmissionLifecycleCommand(command.eventId(), "c".repeat(64), command.fact())))
                .hasMessageContaining("payload changed");
        var wrong = new AdmissionLifecycleCommand(UUID.randomUUID(), "d".repeat(64),
                new AdmissionLifecycleFact(AdmissionLifecycleFact.Kind.CLOSED, admissionId, UUID.randomUUID(),
                        null, startedAt.plusSeconds(10), "e".repeat(64)));
        assertThatThrownBy(() -> service.project(wrong)).hasMessageContaining("identity changed");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_medication_event", Integer.class)).isOne();
        assertThat(reload().closedAt()).isNull();
    }

    @Test
    void sameFactNewEventId_recordsDeliveryWithoutAdvancingContextVersion() {
        var command = start();
        service.project(command);
        service.project(command);
        service.project(new AdmissionLifecycleCommand(UUID.randomUUID(), "c".repeat(64), command.fact()));
        assertThat(reload().version()).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_medication_event", Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentStartAndClose_alwaysEndClosed() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var first = executor.submit(() -> { await(ready, go); service.project(start()); });
            var second = executor.submit(() -> { await(ready, go); service.project(close()); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
        assertThat(reload().closedAt()).isNotNull();
        assertThat(reload().version()).isEqualTo(2);
    }

    @Test
    void partialStartTuple_isRejectedByDatabase() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO admission_medication_context(admission_id, patient_id, department_id)
                VALUES (?, ?, ?)
                """, admissionId, patientId, departmentId)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private AdmissionMedicationContext reload() {
        return new TransactionTemplate(transactions).execute(status -> contexts.lockOrCreate(admissionId, patientId));
    }

    private AdmissionLifecycleCommand start() {
        return new AdmissionLifecycleCommand(UUID.randomUUID(), "a".repeat(64),
                new AdmissionLifecycleFact(AdmissionLifecycleFact.Kind.STARTED, admissionId, patientId,
                        departmentId, startedAt, "a".repeat(64)));
    }

    private AdmissionLifecycleCommand close() {
        return new AdmissionLifecycleCommand(UUID.randomUUID(), "b".repeat(64),
                new AdmissionLifecycleFact(AdmissionLifecycleFact.Kind.CLOSED, admissionId, patientId,
                        null, startedAt.plusSeconds(100), "b".repeat(64)));
    }

    private static void await(CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
