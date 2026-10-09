package com.mediflow.pharmacy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;
import com.mediflow.pharmacy.application.mapper.DispenseDtoMapper;
import com.mediflow.pharmacy.application.port.out.*;
import com.mediflow.pharmacy.application.service.CareDispenseTransactionService;
import com.mediflow.pharmacy.application.service.CarePrescriptionTerminalService;
import com.mediflow.pharmacy.application.service.CarePrescriptionCreationService;
import com.mediflow.pharmacy.application.service.ContextCheckedPrescriptionCreationService;
import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.dto.command.CancelPrescriptionCommand;
import com.mediflow.pharmacy.application.dto.command.CreatePrescriptionCommand;
import com.mediflow.pharmacy.application.dto.request.CreatePrescriptionRequest;
import com.mediflow.pharmacy.application.dto.request.PrescriptionLineRequest;
import com.mediflow.pharmacy.application.service.PrescriptionCareEventCaptureService;
import com.mediflow.pharmacy.application.service.PrescriptionClearanceAuthorizationService;
import com.mediflow.pharmacy.domain.model.*;
import com.mediflow.pharmacy.domain.model.enums.*;
import com.mediflow.pharmacy.infrastructure.messaging.PharmacyEventPublisherAdapter;
import com.mediflow.pharmacy.infrastructure.messaging.PrescriptionCareEventCodec;
import com.mediflow.pharmacy.infrastructure.messaging.PrescriptionCareEventWriterAdapter;
import com.mediflow.pharmacy.infrastructure.persistence.adapter.*;

/** Real internal stock + held-outbox transaction, never a live owner contract/Rabbit acceptance. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CareDispenseTransactionService.class, CarePrescriptionTerminalService.class, CarePrescriptionCreationService.class,
        ContextCheckedPrescriptionCreationService.class,
        CarePrescriptionCreationPersistenceAdapter.class, PrescriptionCareEventCaptureService.class,
        PrescriptionClearanceAuthorizationService.class, PrescriptionClearancePersistenceAdapter.class,
        PrescriptionPersistenceAdapter.class, DispenseSlipPersistenceAdapter.class, DrugPersistenceAdapter.class,
        StockReservationPersistenceAdapter.class, PrescriptionCareEventWriterAdapter.class, PrescriptionCareEventCodec.class,
        PharmacyEventPublisherAdapter.class, CareDispensePostgresTest.Config.class})
@Testcontainers(disabledWithoutDocker = true)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CareDispensePostgresTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired CareDispenseTransactionService service;
    @Autowired CarePrescriptionTerminalService terminal;
    @Autowired CarePrescriptionCreationService creation;
    @Autowired ContextCheckedPrescriptionCreationService contextCreation;
    @MockBean OutpatientPrescriptionContextPort contextSource;
    @MockBean PrescriptionIdentityPort identitySource;
    @Autowired PrescriptionCareEventCaptureService capture;
    @Autowired PrescriptionRepositoryPort prescriptions;
    @Autowired DispenseSlipRepositoryPort slips;
    @Autowired DrugRepositoryPort drugs;
    @Autowired StockReservationRepositoryPort reservations;
    @Autowired PrescriptionClearancePort clearances;
    @Autowired PrescriptionCareEventCodec codec;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Clock clock;

    @BeforeEach void clean() {
        jdbc.execute("""
                TRUNCATE PHARMACY_EVENT_OUTBOX, prescription_clearance_event, prescription_clearance, prescription_clearance_target,
                    STOCK_RESERVATION, DISPENSE_SLIP, PRESCRIPTION_LINE, PRESCRIPTION, DRUG CASCADE
                """);
    }

    @Test void dispense_commitsStockReservationProofAndHeldBytesThenRetryIsNoOp() {
        var seed = seed(true);
        var actor = DispenseActor.account(UUID.randomUUID());
        var first = service.execute(seed.prescriptionId(), actor, "internal-command");
        assertThat(first.status()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(service.execute(seed.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "retry")).isEqualTo(first);
        assertThat(stock(seed.drugId())).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM STOCK_RESERVATION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("FULFILLED");
        assertThat(jdbc.queryForObject("SELECT target_status FROM prescription_clearance WHERE clearance_id = ?", String.class, seed.clearanceId())).isEqualTo("VERIFIED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE care_contract_version = 1 AND NOT delivery_enabled", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE routing_key = 'stock.low'", Integer.class)).isOne();
    }
    @Test void missingHeldCreation_rollsBackStockAndAuthorizationThenCreationAllowsRetry() {
        var seed = seed(false);
        assertThatThrownBy(() -> service.execute(seed.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "first"))
                .hasMessageContaining("creation");
        assertThat(stock(seed.drugId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM PRESCRIPTION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM DISPENSE_SLIP WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM STOCK_RESERVATION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("SELECT target_status FROM prescription_clearance WHERE clearance_id = ?", String.class, seed.clearanceId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX", Integer.class)).isZero();
        tx(() -> capture.capture(seed.prescriptionId(), UUID.randomUUID(), EventType.CREATED, "creation"));
        service.execute(seed.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "retry");
        assertThat(stock(seed.drugId())).isOne();
    }
    @Test void actualNanos_reloadedPrescriptionSlipAndHeldEventUseOneExactBusinessInstant() {
        var seed = seed(true);
        service.execute(seed.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "exact-time");
        tx(() -> {
            var prescription = prescriptions.findByIdForUpdate(seed.prescriptionId()).orElseThrow();
            var slip = slips.findByPrescriptionForUpdate(seed.prescriptionId()).orElseThrow();
            assertThat(prescription.getLifecycleAt()).isEqualTo(clock.instant());
            assertThat(slip.getLifecycleAt()).isEqualTo(clock.instant());
            assertThat(slip.getDispensedAt()).isEqualTo(clock.instant());
            String json = jdbc.queryForObject("SELECT payload FROM PHARMACY_EVENT_OUTBOX WHERE aggregate_id = ? AND care_lifecycle_order = 1", String.class, seed.prescriptionId());
            assertThat(codec.decode("prescription.filled", json.getBytes(java.nio.charset.StandardCharsets.UTF_8)).occurredAt()).isEqualTo(clock.instant());
        });
    }
    @Test void concurrentCommands_onlyOneStockMutationAndOneHeldTerminal() throws Exception {
        var seed = seed(true);
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { assertThat(gate.await(5, TimeUnit.SECONDS)).isTrue(); return service.execute(seed.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "a"); });
            var b = executor.submit(() -> { assertThat(gate.await(5, TimeUnit.SECONDS)).isTrue(); return service.execute(seed.prescriptionId(), DispenseActor.account(UUID.randomUUID()), "b"); });
            gate.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
        }
        assertThat(stock(seed.drugId())).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE care_lifecycle_order = 1", Integer.class)).isOne();
    }
    @Test void missingClearance_deniesWithoutLegacyFailureOrRefundEffect() {
        var seed = seed(true);
        jdbc.update("DELETE FROM prescription_clearance WHERE clearance_id = ?", seed.clearanceId());
        assertThatThrownBy(() -> service.execute(seed.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "denied"))
                .hasMessageContaining("clearance");
        assertThat(stock(seed.drugId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM DISPENSE_SLIP WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE routing_key <> 'prescription.created'", Integer.class)).isZero();
    }

    @Test void missingHeldTerminalProof_retryFailsWithoutAnotherStockEffect() {
        var seed = seed(true);
        service.execute(seed.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "first");
        jdbc.update("DELETE FROM PHARMACY_EVENT_OUTBOX WHERE aggregate_id = ? AND care_lifecycle_order = 1", seed.prescriptionId());
        assertThatThrownBy(() -> service.execute(seed.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "retry"))
                .hasMessageContaining("proof is missing");
        assertThat(stock(seed.drugId())).isOne();
    }

    @Test void cancel_commitsReleaseAndHeldFactThenRetryDoesNotDecrementStock() {
        var seed = seed(true);
        var command = cancelCommand(seed.prescriptionId());
        var first = terminal.cancelCare(command);
        var retry = terminal.cancelCare(command);
        assertThat(first.releasedReservations()).isOne();
        assertThat(retry.releasedReservations()).isZero();
        assertThat(first.cancelledAt()).isEqualTo(clock.instant()).isEqualTo(retry.cancelledAt());
        assertThat(stock(seed.drugId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM STOCK_RESERVATION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE care_lifecycle_order = 1 AND NOT delivery_enabled", Integer.class)).isOne();
    }

    @Test void cancel_writerFailure_rollsBackEveryReleaseAndTerminalProof() {
        var seed = seed(false);
        assertThatThrownBy(() -> terminal.cancelCare(cancelCommand(seed.prescriptionId()))).hasMessageContaining("creation");
        assertThat(jdbc.queryForObject("SELECT status FROM PRESCRIPTION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM DISPENSE_SLIP WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM STOCK_RESERVATION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("RESERVED");
    }

    @Test void expire_boundary_commitsWholeOrderAndExactProofThenRetryNoOp() {
        var seed = seed(true);
        jdbc.update("UPDATE STOCK_RESERVATION SET expires_at = ? WHERE prescription_id = ?",
                java.sql.Timestamp.from(clock.instant().minusSeconds(1)), seed.prescriptionId());
        assertThat(terminal.expireCare(seed.prescriptionId(), "expiry")).isOne();
        assertThat(terminal.expireCare(seed.prescriptionId(), "retry")).isZero();
        tx(() -> {
            assertThat(prescriptions.findById(seed.prescriptionId()).orElseThrow().getLifecycleAt()).isEqualTo(clock.instant());
            assertThat(slips.findByPrescription(seed.prescriptionId()).orElseThrow().getLifecycleAt()).isEqualTo(clock.instant());
        });
        assertThat(stock(seed.drugId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT status FROM STOCK_RESERVATION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("EXPIRED");
    }

    @Test void cancelRacesDispense_onlyOneTerminalAndConsistentStockOutcome() throws Exception {
        var seed = seed(true);
        var command = cancelCommand(seed.prescriptionId());
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var cancellation = executor.submit(() -> { gate.await(); try { terminal.cancelCare(command); return true; } catch (com.mediflow.common.exception.BusinessRuleException e) { return false; } });
            var dispensing = executor.submit(() -> { gate.await(); try { service.execute(seed.prescriptionId(), DispenseActor.account(UUID.randomUUID()), "race"); return true; } catch (com.mediflow.common.exception.BusinessRuleException e) { return false; } });
            gate.countDown();
            assertThat(cancellation.get(15, TimeUnit.SECONDS) ^ dispensing.get(15, TimeUnit.SECONDS)).isTrue();
        }
        String status = jdbc.queryForObject("SELECT status FROM PRESCRIPTION WHERE prescription_id = ?", String.class, seed.prescriptionId());
        assertThat(stock(seed.drugId())).isEqualTo("CANCELLED".equals(status) ? 3 : 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE care_lifecycle_order = 1", Integer.class)).isOne();
    }

    private CancelPrescriptionCommand cancelCommand(UUID id) {
        return new CancelPrescriptionCommand(id, new ActorIdentity(UUID.randomUUID(), null, "ADMIN"), "Changed treatment", "cancel");
    }

    @Test void creation_contextCheckedProofCommitsExactRecordAndReplayKeepsOneHeldEvent() {
        var original = creationCommand(); var r = original.request(); var record = UUID.randomUUID();
        var command = new CreatePrescriptionCommand(new CreatePrescriptionRequest(record, r.patientId(), r.doctorId(), r.departmentId(),
                r.prescribedDate(), r.lines(), 1, r.careContext(), r.careEpisodeType(), r.careEpisodeId(), null, r.priceCode()), original.actor(), original.correlationId());
        var proof = new OutpatientPrescriptionContextPort.Observation(true, record, r.patientId(), r.doctorId(), r.departmentId(),
                "OUTPATIENT_VISIT", r.careEpisodeId(), "OPEN", null, clock.instant());
        UUID key = UUID.randomUUID(), id = creation.createCare(key, command, proof);
        assertThat(creation.createCare(key, command, proof)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT record_id FROM PRESCRIPTION WHERE prescription_id=?", UUID.class, id)).isEqualTo(record);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE care_contract_version=1", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM STOCK_RESERVATION WHERE prescription_id=?", Integer.class, id)).isOne();
        var stale = new OutpatientPrescriptionContextPort.Observation(true, record, r.patientId(), r.doctorId(), r.departmentId(),
                "OUTPATIENT_VISIT", r.careEpisodeId(), "OPEN", null, clock.instant().minusSeconds(31));
        assertThatThrownBy(() -> creation.createCare(key, command, stale)).isInstanceOf(com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE care_contract_version=1", Integer.class)).isOne();
    }

    @Test void creation_contextPreflightRunsOutsideTransactionAndStockWriterCommitsAtomically() {
        var original = creationCommand(); var r = original.request(); var record = UUID.randomUUID();
        var command = new CreatePrescriptionCommand(new CreatePrescriptionRequest(record, r.patientId(), r.doctorId(), r.departmentId(),
                r.prescribedDate(), r.lines(), 1, r.careContext(), r.careEpisodeType(), r.careEpisodeId(), null, r.priceCode()), original.actor(), original.correlationId());
        var proof = new OutpatientPrescriptionContextPort.Observation(true, record, r.patientId(), r.doctorId(), r.departmentId(),
                "OUTPATIENT_VISIT", r.careEpisodeId(), "OPEN", null, clock.instant());
        org.mockito.Mockito.when(contextSource.findRecord(record, command.correlationId())).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return proof;
        });
        org.mockito.Mockito.when(identitySource.lookup(r.patientId(), r.doctorId(), r.departmentId(), command.correlationId())).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new PrescriptionIdentityPort.Observation(r.patientId(), true, r.doctorId(), true, true,
                    r.departmentId(), r.departmentId(), true, clock.instant());
        });
        UUID id = contextCreation.createWithContext(UUID.randomUUID(), command);
        assertThat(jdbc.queryForObject("SELECT record_id FROM PRESCRIPTION WHERE prescription_id=?", UUID.class, id)).isEqualTo(record);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE care_contract_version=1", Integer.class)).isOne();
    }

    @Test void creation_contextPreflightRejectsAmbientTransactionBeforeRemoteOrStockEffects() {
        var command = creationCommand();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status ->
                contextCreation.createWithContext(UUID.randomUUID(), command)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        org.mockito.Mockito.verifyNoInteractions(contextSource);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PRESCRIPTION", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX", Integer.class)).isZero();
    }

    @Test void creation_currentIdentityDenialCannotClaimCommandReserveStockOrCreateHeldEvent() {
        var original = creationCommand(); var r = original.request(); var record = UUID.randomUUID();
        var command = new CreatePrescriptionCommand(new CreatePrescriptionRequest(record, r.patientId(), r.doctorId(), r.departmentId(),
                r.prescribedDate(), r.lines(), 1, r.careContext(), r.careEpisodeType(), r.careEpisodeId(), null, r.priceCode()), original.actor(), original.correlationId());
        org.mockito.Mockito.when(contextSource.findRecord(record, command.correlationId())).thenReturn(
                new OutpatientPrescriptionContextPort.Observation(true, record, r.patientId(), r.doctorId(), r.departmentId(),
                        "OUTPATIENT_VISIT", r.careEpisodeId(), "OPEN", null, clock.instant()));
        org.mockito.Mockito.when(identitySource.lookup(r.patientId(), r.doctorId(), r.departmentId(), command.correlationId())).thenReturn(
                new PrescriptionIdentityPort.Observation(r.patientId(), true, r.doctorId(), true, false, null,
                        r.departmentId(), true, clock.instant()));
        assertThatThrownBy(() -> contextCreation.createWithContext(UUID.randomUUID(), command))
                .isInstanceOf(com.mediflow.common.exception.BusinessRuleException.class);
        for (String table : new String[]{"PRESCRIPTION", "STOCK_RESERVATION", "PHARMACY_EVENT_OUTBOX", "DISPENSE_SLIP", "care_prescription_creation"})
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).as(table).isZero();
    }

    @Test void creation_retryAndChangedIntent_haveOneAggregateAndOneHeldCreation() {
        var command = creationCommand();
        UUID key = UUID.randomUUID();
        UUID id = creation.createCare(key, command);
        assertThat(creation.createCare(key, command)).isEqualTo(id);
        var request = command.request();
        var changed = new CreatePrescriptionCommand(new CreatePrescriptionRequest(null, request.patientId(), request.doctorId(),
                request.departmentId(), request.prescribedDate(), List.of(new PrescriptionLineRequest(request.lines().getFirst().drugId(), 1, "Daily")),
                1, request.careContext(), request.careEpisodeType(), request.careEpisodeId(), null, request.priceCode()), command.actor(), "changed");
        assertThatThrownBy(() -> creation.createCare(key, changed)).hasMessageContaining("another actor/intent");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PRESCRIPTION", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE care_lifecycle_order = 0 AND NOT delivery_enabled", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM care_prescription_creation WHERE prescription_id = ?", Integer.class, id)).isOne();
    }

    @Test void creation_outboxFailure_rollsBackReceiptReservationsAndPrescriptionThenRetrySucceeds() {
        var command = creationCommand();
        UUID key = UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION reject_care_creation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected held creation failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_care_creation BEFORE INSERT ON PHARMACY_EVENT_OUTBOX FOR EACH ROW EXECUTE FUNCTION reject_care_creation()");
        try {
            assertThatThrownBy(() -> creation.createCare(key, command)).hasMessageContaining("injected held creation failure");
            for (String table : List.of("care_prescription_creation", "PRESCRIPTION", "DISPENSE_SLIP", "STOCK_RESERVATION")) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).isZero();
            }
        } finally {
            jdbc.execute("DROP TRIGGER fail_care_creation ON PHARMACY_EVENT_OUTBOX");
            jdbc.execute("DROP FUNCTION reject_care_creation()");
        }
        assertThat(creation.createCare(key, command)).isNotNull();
    }

    @Test void concurrentCreation_sameCommand_hasOneReservationAndStableResult() throws Exception {
        var command = creationCommand();
        UUID key = UUID.randomUUID();
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { gate.await(); return creation.createCare(key, command); });
            var b = executor.submit(() -> { gate.await(); return creation.createCare(key, command); });
            gate.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM STOCK_RESERVATION", Integer.class)).isOne();
    }

    @Test void internalCreationThenExactGrantAndDispense_preservesAllHeldLifecycleFacts() {
        var command = creationCommand();
        UUID id = creation.createCare(UUID.randomUUID(), command);
        tx(() -> {
            var p = prescriptions.findByIdForUpdate(id).orElseThrow();
            var grant = new PrescriptionClearance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), id,
                    p.getPatientId(), p.getCareContext().episode(), new BigDecimal("200.00"), "VND", "CASH",
                    clock.instant().minusSeconds(1), clock.instant().plusSeconds(600), "a".repeat(64));
            clearances.lockTarget(id);
            clearances.store(grant, false);
        });
        var outcome = service.execute(id, command.actor().dispenseAuditActor(), "internal-dispense");
        assertThat(outcome.status()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(stock(command.request().lines().getFirst().drugId())).isOne();
        assertThat(jdbc.queryForList("SELECT care_lifecycle_order FROM PHARMACY_EVENT_OUTBOX WHERE care_contract_version = 1 ORDER BY care_lifecycle_order", Integer.class))
                .containsExactly(0, 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE care_contract_version = 1 AND delivery_enabled", Integer.class)).isZero();
        // This is module-local PG proof, not actual Billing producer bytes or cross-service E2E.
    }

    @Test void expire_missingHeldCreation_rollsBackWholeOrderRelease() {
        var seed = seed(false);
        jdbc.update("UPDATE STOCK_RESERVATION SET expires_at = ? WHERE prescription_id = ?",
                java.sql.Timestamp.from(clock.instant().minusSeconds(1)), seed.prescriptionId());
        assertThatThrownBy(() -> terminal.expireCare(seed.prescriptionId(), "expiry")).hasMessageContaining("creation");
        assertThat(jdbc.queryForObject("SELECT status FROM PRESCRIPTION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM STOCK_RESERVATION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("RESERVED");
    }

    @Test void stockFailure_realShortage_commitsReleaseAndHeldFailureWithoutRefundOrStockDecrement() {
        var seed = seed(true);
        jdbc.update("UPDATE DRUG SET stock_quantity = 1 WHERE drug_id = ?", seed.drugId());
        var actor = DispenseActor.staff(UUID.randomUUID());
        assertThatThrownBy(() -> service.execute(seed.prescriptionId(), actor, "dispense")).hasMessageContaining("Insufficient stock");
        var result = service.recordStockFailure(seed.prescriptionId(), actor, "stock-failure");
        assertThat(result.orElseThrow().status()).isEqualTo(DispenseStatus.FAILED);
        assertThat(service.recordStockFailure(seed.prescriptionId(), actor, "retry")).isEqualTo(result);
        assertThat(stock(seed.drugId())).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM STOCK_RESERVATION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE routing_key = 'prescription.dispense.failed' AND care_contract_version = 1 AND NOT delivery_enabled", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_RECEIPT", Integer.class)).isZero();
    }

    @Test void stockFailure_noCreationProof_rollsBackFailureReleaseAndGrantVerification() {
        var seed = seed(false);
        jdbc.update("UPDATE DRUG SET stock_quantity = 1 WHERE drug_id = ?", seed.drugId());
        assertThatThrownBy(() -> service.recordStockFailure(seed.prescriptionId(), DispenseActor.staff(UUID.randomUUID()), "failure")).hasMessageContaining("creation");
        assertThat(jdbc.queryForObject("SELECT status FROM PRESCRIPTION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM STOCK_RESERVATION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("SELECT target_status FROM prescription_clearance WHERE clearance_id = ?", String.class, seed.clearanceId())).isEqualTo("PENDING");
    }

    @Test void stockFailure_missingClearance_neverConvertsAuthorizationDenialToTerminalFailure() {
        var seed = seed(true);
        jdbc.update("UPDATE DRUG SET stock_quantity = 1 WHERE drug_id = ?", seed.drugId());
        jdbc.update("DELETE FROM prescription_clearance WHERE clearance_id = ?", seed.clearanceId());
        assertThatThrownBy(() -> service.recordStockFailure(seed.prescriptionId(), DispenseActor.account(UUID.randomUUID()), "failure"))
                .isInstanceOf(com.mediflow.pharmacy.domain.exception.DispenseAuthorizationException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM PRESCRIPTION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM STOCK_RESERVATION WHERE prescription_id = ?", String.class, seed.prescriptionId())).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM PHARMACY_EVENT_OUTBOX WHERE care_lifecycle_order = 1", Integer.class)).isZero();
    }

    private CreatePrescriptionCommand creationCommand() {
        LocalDate today = clock.instant().atZone(clock.getZone()).toLocalDate();
        UUID drugId = new TransactionTemplate(transactions).execute(status -> drugs.save(Drug.create("Server name", null, "tablet",
                new BigDecimal("50.00"), 3, today.plusYears(1), null, 1, today)).getDrugId());
        var actor = new ActorIdentity(UUID.randomUUID(), UUID.randomUUID(), "DOCTOR");
        return new CreatePrescriptionCommand(new CreatePrescriptionRequest(null, UUID.randomUUID(), actor.staffId(), UUID.randomUUID(),
                today, List.of(new PrescriptionLineRequest(drugId, 2, "Daily")), 1, CareContext.OUTPATIENT,
                CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, "DRUG"), actor, "internal-creation");
    }

    private Seed seed(boolean holdCreation) {
        return new TransactionTemplate(transactions).execute(status -> {
            var today = clock.instant().atZone(clock.getZone()).toLocalDate();
            var drug = drugs.save(Drug.create("Historical name", null, "tablet", new BigDecimal("50.00"),
                    3, today.plusYears(1), null, 1, today));
            var episode = new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID());
            var prescription = prescriptions.save(Prescription.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), today, List.of(PrescriptionLine.create(drug.getDrugId(), 2, drug.getPrice(), "Daily", drug.getDrugName())),
                    PrescriptionCareContext.v1(CareContext.OUTPATIENT, episode, null, "DRUG")));
            slips.save(DispenseSlip.createPending(prescription.getPrescriptionId()));
            reservations.save(StockReservation.create(drug.getDrugId(), prescription.getPrescriptionId(), 2, clock.instant().plusSeconds(600)));
            var grant = new PrescriptionClearance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), prescription.getPrescriptionId(),
                    prescription.getPatientId(), episode, new BigDecimal("200.00"), "VND", "CASH", clock.instant().minusSeconds(1),
                    clock.instant().plusSeconds(600), "a".repeat(64));
            clearances.lockTarget(prescription.getPrescriptionId());
            clearances.store(grant, false);
            if (holdCreation) capture.capture(prescription.getPrescriptionId(), UUID.randomUUID(), EventType.CREATED, "created");
            return new Seed(prescription.getPrescriptionId(), drug.getDrugId(), grant.clearanceId());
        });
    }
    private int stock(UUID drugId) { return jdbc.queryForObject("SELECT stock_quantity FROM DRUG WHERE drug_id = ?", Integer.class, drugId); }
    private void tx(Runnable action) { new TransactionTemplate(transactions).executeWithoutResult(tx -> action.run()); }
    private record Seed(UUID prescriptionId, UUID drugId, UUID clearanceId) { }
    @TestConfiguration static class Config {
        @Bean java.time.Duration reservationTtl() { return java.time.Duration.ofHours(24); }
        @Bean Clock clock() {
            return Clock.fixed(Instant.now().plusSeconds(60).with(java.time.temporal.ChronoField.NANO_OF_SECOND, 123456789), ZoneOffset.UTC);
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean DispenseDtoMapper dispenseDtoMapper() { return Mappers.getMapper(DispenseDtoMapper.class); }
    }
}
