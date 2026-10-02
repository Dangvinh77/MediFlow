package com.mediflow.pharmacy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.mediflow.pharmacy.support.ClearanceTestFixtures.*;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.Clock;
import java.time.ZoneOffset;

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

import com.mediflow.pharmacy.application.dto.command.PrescriptionClearanceCommand;
import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.application.service.PrescriptionClearanceApplicationService;
import com.mediflow.pharmacy.application.service.PrescriptionClearanceAuthorizationService;
import com.mediflow.pharmacy.domain.model.PrescriptionClearance;
import com.mediflow.pharmacy.infrastructure.persistence.adapter.PrescriptionClearancePersistenceAdapter;
import com.mediflow.pharmacy.infrastructure.persistence.adapter.PrescriptionPersistenceAdapter;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PrescriptionClearancePersistenceAdapter.class, PrescriptionPersistenceAdapter.class,
        PrescriptionClearanceApplicationService.class, PrescriptionClearanceAuthorizationService.class,
        PrescriptionClearancePostgresTest.Config.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PrescriptionClearancePostgresTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired PrescriptionClearanceApplicationService service;
    @Autowired PrescriptionClearanceAuthorizationService authorizer;
    @Autowired PrescriptionClearancePersistenceAdapter clearances;
    @Autowired PrescriptionRepositoryPort prescriptions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @TestConfiguration
    static class Config {
        @Bean Clock clock() { return Clock.fixed(GRANTED_AT.plusSeconds(1), ZoneOffset.UTC); }
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE prescription_clearance_event, prescription_clearance, prescription_clearance_target");
        jdbc.execute("TRUNCATE prescription, drug CASCADE");
    }

    @Test
    void project_earlyGrant_survivesReloadAndVerifiesOnlyExactLatePrescription() {
        var grant = grant();
        service.project(command(grant));
        assertThat(status(grant)).isEqualTo("PENDING");
        assertThat(count("prescription")).isZero();
        seedPrescription(grant);
        var accepted = new TransactionTemplate(transactions).execute(tx -> authorizer.requireValidAfterLocks(
                prescriptions.findByIdForUpdate(grant.prescriptionId()).orElseThrow()));
        assertThat(accepted).isEqualTo(grant);
        assertThat(accepted.grantedAt()).isEqualTo(GRANTED_AT);
        assertThat(accepted.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(status(grant)).isEqualTo("VERIFIED");
        assertThat(count("payment_receipt")).isZero();
        assertThat(count("dispense_slip")).isZero();
        assertThat(count("pharmacy_event_outbox")).isZero();
    }

    @Test
    void project_wrongKnownPatient_rollsBackClaimAndTargetFence() {
        var grant = grant();
        seedPrescription(withPatient(grant, UUID.randomUUID()));
        assertThatThrownBy(() -> service.project(command(grant))).hasMessageContaining("exact V1");
        assertThat(count("prescription_clearance_event")).isZero();
        assertThat(count("prescription_clearance_target")).isZero();
        assertThat(count("prescription_clearance")).isZero();
    }

    @Test
    void authorize_wrongLatePatientOrExactExpiry_keepsPendingAndAllEffectsUnchanged() {
        var grant = grant();
        service.project(command(grant));
        seedPrescription(withPatient(grant, UUID.randomUUID()));
        assertThatThrownBy(() -> authorize(grant)).hasMessageContaining("non-expired");
        assertThat(status(grant)).isEqualTo("PENDING");
        jdbc.update("UPDATE prescription SET patient_id = ? WHERE prescription_id = ?",
                grant.patientId(), grant.prescriptionId());
        var expired = new PrescriptionClearanceAuthorizationService(clearances, Clock.fixed(EXPIRES_AT, ZoneOffset.UTC));
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(tx -> expired.requireValidAfterLocks(
                prescriptions.findByIdForUpdate(grant.prescriptionId()).orElseThrow())))
                .hasMessageContaining("non-expired");
        assertThat(status(grant)).isEqualTo("PENDING");
        assertThat(count("payment_receipt")).isZero();
        assertThat(count("pharmacy_event_outbox")).isZero();
    }

    @Test
    void project_duplicateAndNewEventSameGrant_hasOneSemanticEffect() {
        var grant = grant();
        var command = command(grant);
        service.project(command);
        service.project(command);
        service.project(command(grant));
        assertThat(count("prescription_clearance")).isOne();
        assertThat(count("prescription_clearance_event")).isEqualTo(2);
    }

    @Test
    void project_eventOrClearanceIdentityChanged_rollsBackConflictingDelivery() {
        var grant = grant();
        var original = command(grant);
        service.project(original);
        var changedPatient = withPatient(grant, UUID.randomUUID());
        assertThatThrownBy(() -> service.project(new PrescriptionClearanceCommand(
                original.eventId(), original.eventFingerprint(), changedPatient))).hasMessageContaining("Event identity");
        assertThatThrownBy(() -> service.project(command(changedPatient))).hasMessageContaining("Clearance identity");
        var changedTime = new PrescriptionClearance(grant.clearanceId(), grant.invoiceId(), grant.accountId(),
                grant.prescriptionId(), grant.patientId(), grant.episode(), grant.amount(), grant.currency(),
                grant.paymentMethod(), GRANTED_AT.plusNanos(1), EXPIRES_AT, grant.payloadFingerprint());
        assertThatThrownBy(() -> service.project(command(changedTime))).hasMessageContaining("Clearance identity");
        assertThat(count("prescription_clearance_event")).isOne();
        var stored = new TransactionTemplate(transactions).execute(tx -> clearances.findByTargetForUpdate(grant.prescriptionId()));
        assertThat(stored).containsExactly(grant);
    }

    @Test
    void project_parallelNewEventIdsSameGrant_hasOneGrantAndTwoDeliveries() throws Exception {
        var grant = grant();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var first = executor.submit(() -> { await(ready, go); service.project(command(grant)); });
            var second = executor.submit(() -> { await(ready, go); service.project(command(grant)); });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
        assertThat(count("prescription_clearance")).isOne();
        assertThat(count("prescription_clearance_event")).isEqualTo(2);
    }

    @Test
    void project_storeFailure_rollsBackClaimAndFenceAndRedeliveryCanSucceed() {
        jdbc.execute("""
                CREATE FUNCTION reject_clearance_store() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected clearance store failure'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_clearance BEFORE INSERT ON prescription_clearance
                FOR EACH ROW EXECUTE FUNCTION reject_clearance_store()
                """);
        var command = command(grant());
        try {
            assertThatThrownBy(() -> service.project(command)).hasMessageContaining("injected clearance store failure");
            assertThat(count("prescription_clearance_event")).isZero();
            assertThat(count("prescription_clearance_target")).isZero();
            assertThat(count("prescription_clearance")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_clearance ON prescription_clearance");
            jdbc.execute("DROP FUNCTION reject_clearance_store()");
        }
        service.project(command);
        assertThat(count("prescription_clearance_event")).isOne();
        assertThat(status(command.clearance())).isEqualTo("PENDING");
    }

    @Test
    void authorization_withoutCallerTransaction_isRejected() {
        assertThatThrownBy(() -> authorizer.requireValidAfterLocks(prescription(grant())))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    private PrescriptionClearance authorize(PrescriptionClearance grant) {
        return new TransactionTemplate(transactions).execute(tx -> authorizer.requireValidAfterLocks(
                prescriptions.findByIdForUpdate(grant.prescriptionId()).orElseThrow()));
    }

    private PrescriptionClearanceCommand command(PrescriptionClearance grant) {
        return new PrescriptionClearanceCommand(UUID.randomUUID(), "b".repeat(64), grant);
    }

    private String status(PrescriptionClearance grant) {
        return jdbc.queryForObject("SELECT target_status FROM prescription_clearance WHERE clearance_id = ?",
                String.class, grant.clearanceId());
    }

    private int count(String ownTable) {
        return jdbc.queryForObject("SELECT count(*) FROM " + ownTable, Integer.class);
    }

    /** Own-service storage fixture only; this is not an end-to-end producer workflow. */
    private void seedPrescription(PrescriptionClearance grant) {
        var prescription = prescription(grant);
        var drugId = prescription.getLines().get(0).getDrugId();
        jdbc.update("""
                INSERT INTO drug(drug_id, drug_name, unit, price, stock_quantity, expiry_date)
                VALUES (?, 'Test drug', 'tablet', 100, 10, '2027-10-01')
                """, drugId);
        jdbc.update("""
                INSERT INTO prescription(prescription_id, record_id, patient_id, doctor_id, department_id,
                    prescribed_date, total_amount, status, care_contract_version, care_context,
                    care_episode_type, care_episode_id, price_code)
                VALUES (?, ?, ?, ?, ?, '2026-10-01', 100, 'ACTIVE', 1, 'OUTPATIENT', 'OUTPATIENT_VISIT', ?, 'DRUG')
                """, grant.prescriptionId(), prescription.getRecordId(), grant.patientId(), prescription.getDoctorId(),
                prescription.getDepartmentId(), grant.episode().id());
        jdbc.update("""
                INSERT INTO prescription_line(line_id, prescription_id, drug_id, quantity, unit_price, line_total)
                VALUES (?, ?, ?, 1, 100, 100)
                """, UUID.randomUUID(), grant.prescriptionId(), drugId);
    }

    private static void await(CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        try {
            if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Race timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
