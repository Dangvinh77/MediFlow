package com.mediflow.clinical.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.authority.ConsultationEligibilitySnapshot;
import com.mediflow.clinical.application.port.in.AttachExternalResultUseCase;
import com.mediflow.clinical.application.port.in.ReactToLabResultUseCase;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.application.dto.request.RegistrationCreateRequest;
import com.mediflow.clinical.application.dto.request.PaymentLockCommand;
import com.mediflow.clinical.application.dto.request.PaymentResolutionCommand;
import com.mediflow.clinical.application.event.OutpatientEligibilityFact;
import com.mediflow.clinical.application.dto.response.OutpatientRegistrationDTO;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.ConsultationSessionStatus;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
import com.mediflow.clinical.domain.model.OutpatientPaymentOutcome;
import com.mediflow.clinical.domain.model.OutpatientRegistrationStatus;
import com.mediflow.clinical.domain.model.ExternalResultType;
import com.mediflow.clinical.infrastructure.persistence.adapter.ExternalResultPersistenceAdapter;
import com.mediflow.clinical.messaging.consumer.inbox.InboxEvidenceFactory;
import com.mediflow.common.exception.DuplicateResourceException;

/** PostgreSQL transaction coverage for atomic receipt/effect rollback and redelivery. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.clinical.scheduling.enabled=true",
        "mediflow.jwt.secret=test-secret-must-have-at-least-32-bytes"
})
@Testcontainers(disabledWithoutDocker = true)
class ClinicalIntegrationTransactionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private ReactToLabResultUseCase integration;
    @Autowired private ConsumerInboxBoundary inbox;
    @Autowired private InboxEvidenceFactory evidenceFactory;
    @Autowired private ExternalResultPersistenceAdapter externalResults;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ConsultationSessionRepositoryPort sessions;
    @Autowired private OutpatientRegistrationApplicationService outpatientRegistrations;

    @MockBean
    private AttachExternalResultUseCase attachments;

    @MockBean private CurrentClinicalActorPort actors;
    @MockBean private SchedulingAuthorityPort schedulingAuthority;
    @MockBean private PatientLookupPort patients;
    @MockBean(name = "clinicalSchedulingClock") private java.time.Clock schedulingClock;
    @org.junit.jupiter.api.io.TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS)
    java.nio.file.Path processDirectory;

    @BeforeEach
    void useSubMicrosecondClockToExercisePostgresReceiptPrecision() {
        when(schedulingClock.instant()).thenAnswer(ignored -> Instant.now()
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusNanos(123456789));
        when(schedulingClock.getZone()).thenReturn(java.time.ZoneOffset.UTC);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedEffect_rollsBackDeliveryAndOperationThenRetryCommitsOneAttachment() {
        assertThat(AopUtils.isAopProxy(inbox)).isTrue();

        UUID eventId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();
        insertMedicalRecord(recordId);

        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("database unavailable");
            }
            externalResults.attach(recordId, ExternalResultType.LAB, labId, "Normal");
            return true;
        }).when(attachments).attachLabResult(recordId, labId, "Normal");

        byte[] body = ("{\"eventId\":\"" + eventId + "\",\"eventType\":\"lab.result.created\","
                + "\"recordId\":\"" + recordId + "\",\"labId\":\"" + labId
                + "\",\"conclusion\":\"Normal\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ConsumerInboxClaim claim = evidenceFactory.create("clinical-lab-result", "lab.result.created",
                eventId, "clinical:lab-result:v0:" + labId, body);

        assertThatThrownBy(() -> inbox.execute(claim, () -> integration.onLabResultCreated(
                new com.mediflow.clinical.application.dto.command.LabResultCreatedCommand(
                        eventId, recordId, labId, "Normal"))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("consumer_inbox_delivery", eventId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consumer_inbox_operation WHERE operation_key = ?",
                Integer.class, "clinical:lab-result:v0:" + labId)).isZero();

        inbox.execute(claim, () -> integration.onLabResultCreated(
                new com.mediflow.clinical.application.dto.command.LabResultCreatedCommand(
                        eventId, recordId, labId, "Normal")));

        assertThat(attempts).hasValue(2);
        assertThat(count("consumer_inbox_delivery", eventId)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consumer_inbox_operation WHERE operation_key = ?",
                Integer.class, "clinical:lab-result:v0:" + labId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM attached_result WHERE record_id = ? AND type = ? AND reference_id = ?",
                Integer.class, recordId, "LAB", labId)).isOne();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentLastSeatRegistration_hasOneWinnerAndExactReplayDoesNotRepeatEffects() throws Exception {
        UUID actorAccountId = UUID.randomUUID();
        UUID actorStaffId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ActorIdentity actor = new ActorIdentity(actorAccountId, actorStaffId, null, "NURSE");
        when(actors.current()).thenReturn(actor);
        when(patients.exists(org.mockito.ArgumentMatchers.any(UUID.class))).thenReturn(true);
        Instant now = Instant.now();
        LocalDate sessionDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);
        ConsultationSession session = ConsultationSession.open(sessionId, doctorId, departmentId, roomId,
                sessionDate, LocalTime.of(9, 0), LocalTime.of(10, 0), List.of(), 1);
        sessions.insert(session, actorAccountId, "test", now);
        when(schedulingAuthority.authority(org.mockito.ArgumentMatchers.eq(actorAccountId),
                org.mockito.ArgumentMatchers.eq(departmentId), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new SchedulingAuthoritySnapshot(actorAccountId, true, true, "NURSE", actorStaffId,
                        true, departmentId, true, false, 0L, now));
        when(schedulingAuthority.consultationEligibility(org.mockito.ArgumentMatchers.eq(doctorId),
                org.mockito.ArgumentMatchers.eq(departmentId), org.mockito.ArgumentMatchers.eq(roomId),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ConsultationEligibilitySnapshot(doctorId, true, true, true, departmentId,
                        departmentId, true, true, roomId, true, true, departmentId, "CONSULTATION", true, now));

        UUID firstPatient = UUID.randomUUID();
        UUID secondPatient = UUID.randomUUID();
        RegistrationCreateRequest firstRequest = registrationRequest(firstPatient, session);
        RegistrationCreateRequest secondRequest = registrationRequest(secondPatient, session);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<RegistrationAttempt> first = CompletableFuture.supplyAsync(
                () -> attempt(ready, start, firstRequest, "last-seat-a"));
        CompletableFuture<RegistrationAttempt> second = CompletableFuture.supplyAsync(
                () -> attempt(ready, start, secondRequest, "last-seat-b"));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        RegistrationAttempt firstAttempt = first.get(20, TimeUnit.SECONDS);
        RegistrationAttempt secondAttempt = second.get(20, TimeUnit.SECONDS);

        List<RegistrationAttempt> attempts = List.of(firstAttempt, secondAttempt);
        assertThat(attempts.stream().filter(value -> value.registration() != null)).hasSize(1);
        assertThat(attempts.stream().filter(value -> value.failure() instanceof DuplicateResourceException)
                .map(value -> ((DuplicateResourceException) value.failure()).getCode()))
                .containsExactly("SESSION_CAPACITY_EXHAUSTED");
        OutpatientRegistrationDTO created = attempts.stream().map(RegistrationAttempt::registration)
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outpatient_registration WHERE session_id = ?",
                Integer.class, sessionId)).isOne();
        assertThat(jdbc.queryForObject("SELECT held_count FROM consultation_session WHERE session_id = ?",
                Integer.class, sessionId)).isOne();
        assertThat(jdbc.queryForObject("SELECT available_count FROM consultation_session WHERE session_id = ?",
                Integer.class, sessionId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clinical_outbox_event WHERE aggregate_id = ?",
                Integer.class, created.registrationId())).isOne();

        RegistrationAttempt winner = firstAttempt.registration() == null ? secondAttempt : firstAttempt;
        RegistrationCreateRequest winnerRequest = firstAttempt.registration() == null ? secondRequest : firstRequest;
        String winnerKey = firstAttempt.registration() == null ? "last-seat-b" : "last-seat-a";
        var replay = outpatientRegistrations.create(winnerRequest, winnerKey);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.data().registrationId()).isEqualTo(created.registrationId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM clinical_outbox_event WHERE aggregate_id = ?",
                Integer.class, created.registrationId())).isOne();
        UUID changedPatient = UUID.randomUUID();
        assertThatThrownBy(() -> outpatientRegistrations.create(registrationRequest(changedPatient, session), winnerKey))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting(exception -> ((DuplicateResourceException) exception).getCode())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(winner.registration().registrationId()).isEqualTo(created.registrationId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void overlappingSessionRace_commitsOnlyOneDoctorOrRoomBooking(boolean sharedDoctor) throws Exception {
        UUID doctor = UUID.randomUUID();
        UUID room = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);
        ConsultationSession first = ConsultationSession.open(UUID.randomUUID(), doctor, department, room,
                date, LocalTime.of(9, 0), LocalTime.of(10, 0), List.of(), 2);
        ConsultationSession second = ConsultationSession.open(UUID.randomUUID(),
                sharedDoctor ? doctor : UUID.randomUUID(), department,
                sharedDoctor ? UUID.randomUUID() : room,
                date, LocalTime.of(9, 30), LocalTime.of(10, 30), List.of(), 2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var left = CompletableFuture.supplyAsync(() -> insertSession(ready, start, first, actor));
        var right = CompletableFuture.supplyAsync(() -> insertSession(ready, start, second, actor));
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        var results = List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS));
        assertThat(results).containsExactlyInAnyOrder("COMMITTED", "SESSION_OVERLAP");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consultation_session WHERE session_id IN (?, ?)",
                Integer.class, first.sessionId(), second.sessionId())).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consultation_session_audit WHERE session_id IN (?, ?)",
                Integer.class, first.sessionId(), second.sessionId())).isOne();
    }

    @Test
    void stoppedSession_stillBlocksOverlapButAllowsAdjacentSession() {
        UUID actor = UUID.randomUUID();
        ConsultationSession original = ConsultationSession.open(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(10, 0), List.of(), 2);
        Instant now = Instant.now();
        sessions.insert(original, actor, "test", now);
        ConsultationSession stopped = original.changeStatus(1, ConsultationSessionStatus.STOPPED, "test stop");
        sessions.update(original, stopped, actor, "STATUS_CHANGED", "test stop", now);
        var config = original.configuration();
        ConsultationSession overlap = ConsultationSession.open(UUID.randomUUID(), config.doctorId(),
                config.departmentId(), config.roomId(), config.sessionDate(),
                LocalTime.of(9, 30), LocalTime.of(10, 30), List.of(), 2);
        assertThatThrownBy(() -> sessions.insert(overlap, actor, "test", now))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting(exception -> ((DuplicateResourceException) exception).getCode())
                .isEqualTo("SESSION_OVERLAP");
        ConsultationSession adjacent = ConsultationSession.open(UUID.randomUUID(), config.doctorId(),
                config.departmentId(), config.roomId(), config.sessionDate(),
                LocalTime.of(10, 0), LocalTime.of(11, 0), List.of(), 2);
        sessions.insert(adjacent, actor, "test", now);
        assertThat(sessions.findById(adjacent.sessionId())).contains(adjacent);
        assertThat(sessions.findById(overlap.sessionId())).isEmpty();
    }

    @Test
    void concurrentAcquireAndLateEligibility_keepOneLeaseAndOneQueueNumber() throws Exception {
        OutpatientRegistrationDTO held = heldRegistration();
        var command = paymentCommand(held);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<com.mediflow.clinical.application.dto.request.PaymentLease> acquire = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("race start timed out");
                return outpatientRegistrations.acquirePayment(held.reservationId(), command);
            };
            var left = workers.submit(acquire);
            var right = workers.submit(acquire);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var lease = left.get(20, TimeUnit.SECONDS);
            assertThat(right.get(20, TimeUnit.SECONDS)).isEqualTo(lease);
            assertThat(sessions.findById(held.sessionId()).orElseThrow().counts())
                    .isEqualTo(new ConsultationSession.Counts(0, 1, 0, 0, 0));
            Instant completedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            UUID transaction = UUID.randomUUID();
            UUID clearance = UUID.randomUUID();
            var resolution = new PaymentResolutionCommand(command.paymentOperationId(), command.paymentRequestId(),
                    held.registrationId(), held.reservationId(), lease.fenceToken(), OutpatientPaymentOutcome.COMPLETED,
                    transaction, clearance, completedAt);
            var confirmed = outpatientRegistrations.resolvePayment(resolution);
            assertThat(confirmed.status()).isEqualTo(OutpatientRegistrationStatus.CONFIRMED);
            assertThat(confirmed.queueNumber()).isEqualTo(1);
            assertThat(outpatientRegistrations.resolvePayment(resolution)).isEqualTo(confirmed);
            var late = new OutpatientEligibilityFact(command.paymentOperationId(), command.paymentRequestId(),
                    held.registrationId(), held.reservationId(), lease.fenceToken(), held.patientId(), "EXAM",
                    held.registrationId(), "OUTPATIENT_VISIT", held.registrationId(), transaction, clearance,
                    "100.00", "VND", completedAt, "PATIENT_PAYMENT", null);
            assertThat(outpatientRegistrations.consumeEligibility(late)).isEqualTo(confirmed);
            assertThat(sessions.findById(held.sessionId()).orElseThrow().counts())
                    .isEqualTo(new ConsultationSession.Counts(0, 0, 1, 0, 0));
            assertThat(jdbc.queryForObject("SELECT next_queue_number FROM consultation_session WHERE session_id=?",
                    Integer.class, held.sessionId())).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM clinical_outbox_event WHERE aggregate_id=?",
                    Integer.class, held.registrationId())).isEqualTo(3);
        }
    }

    @Test
    void closedPaymentOperationRejectsLateAcquireAndOldFenceAfterNewLease() {
        OutpatientRegistrationDTO held = heldRegistration();
        var first = paymentCommand(held);
        var lease = outpatientRegistrations.acquirePayment(held.reservationId(), first);
        var closeCommand = new PaymentResolutionCommand(first.paymentOperationId(),
                first.paymentRequestId(), held.registrationId(), held.reservationId(), lease.fenceToken(),
                OutpatientPaymentOutcome.CLOSED_NOT_PAID, null, null, null);
        var closed = outpatientRegistrations.resolvePayment(closeCommand);
        assertThat(outpatientRegistrations.resolvePayment(closeCommand)).isEqualTo(closed);
        assertThatThrownBy(() -> outpatientRegistrations.acquirePayment(held.reservationId(), first))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting(exception -> ((DuplicateResourceException) exception).getCode())
                .isEqualTo("PAYMENT_OPERATION_CLOSED");
        var second = paymentCommand(closed);
        var nextLease = outpatientRegistrations.acquirePayment(held.reservationId(), second);
        assertThat(nextLease.fenceToken()).isGreaterThan(lease.fenceToken());
        assertThatThrownBy(() -> outpatientRegistrations.resolvePayment(new PaymentResolutionCommand(
                first.paymentOperationId(), first.paymentRequestId(), held.registrationId(), held.reservationId(),
                lease.fenceToken(), OutpatientPaymentOutcome.COMPLETED, UUID.randomUUID(), UUID.randomUUID(), Instant.now())))
                .isInstanceOf(DuplicateResourceException.class);
        assertThat(sessions.findById(held.sessionId()).orElseThrow().counts())
                .isEqualTo(new ConsultationSession.Counts(0, 1, 0, 0, 0));
        assertThat(jdbc.queryForObject("SELECT next_queue_number FROM consultation_session WHERE session_id=?",
                Integer.class, held.sessionId())).isEqualTo(1);
        outpatientRegistrations.resolvePayment(new PaymentResolutionCommand(second.paymentOperationId(),
                second.paymentRequestId(), held.registrationId(), held.reservationId(), nextLease.fenceToken(),
                OutpatientPaymentOutcome.CLOSED_NOT_PAID, null, null, null));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completedLeaseAfterHoldExpiry_preservesCashOutcomeAndDoesNotQueueAfterSessionEnd(boolean sessionEnded) {
        OutpatientRegistrationDTO held = heldRegistration();
        var command = paymentCommand(held);
        var lease = outpatientRegistrations.acquirePayment(held.reservationId(), command);
        Instant committedAt = held.reservationExpiresAt().plusSeconds(5)
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant deliveredAt = sessionEnded
                ? held.sessionDate().atTime(held.endTime()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().plusSeconds(1)
                : committedAt.plusSeconds(1);
        when(schedulingClock.instant()).thenReturn(deliveredAt);
        var result = outpatientRegistrations.resolvePayment(new PaymentResolutionCommand(command.paymentOperationId(),
                command.paymentRequestId(), held.registrationId(), held.reservationId(), lease.fenceToken(),
                OutpatientPaymentOutcome.COMPLETED, UUID.randomUUID(), UUID.randomUUID(), committedAt));
        var counts = sessions.findById(held.sessionId()).orElseThrow().counts();
        assertThat(counts.availableCount()).isZero();
        if (sessionEnded) {
            assertThat(result.status()).isEqualTo(OutpatientRegistrationStatus.RECONCILIATION_REQUIRED);
            assertThat(result.queueNumber()).isNull();
            assertThat(counts.paymentLockedCount()).isOne();
            assertThat(counts.confirmedCount()).isZero();
        } else {
            assertThat(result.status()).isEqualTo(OutpatientRegistrationStatus.CONFIRMED);
            assertThat(result.queueNumber()).isEqualTo(1);
            assertThat(counts.confirmedCount()).isOne();
            assertThat(counts.paymentLockedCount()).isZero();
        }
    }

    @Test
    void missingBillingAcrossWorkerProcesses_keepsSeatAndRetriesPersistedCursorBeforeDefinitiveClose() throws Exception {
        var held = heldRegistration();
        var command = paymentCommand(held);
        var lease = outpatientRegistrations.acquirePayment(held.reservationId(), command);
        runReconciliationProcess("MISSING", lease.reconcileAfter().plusSeconds(1), command.paymentRequestId());
        assertThat(sessions.findById(held.sessionId()).orElseThrow().counts())
                .isEqualTo(new ConsultationSession.Counts(0, 1, 0, 0, 0));
        String receiptSql = "SELECT state FROM outpatient_payment_operation_receipt WHERE payment_operation_id=?";
        assertThat(jdbc.queryForObject(receiptSql, String.class, command.paymentOperationId())).isEqualTo("PAYMENT_LOCKED");
        assertThat(jdbc.queryForObject("SELECT recovery_attempt_count FROM outpatient_payment_operation_receipt "
                + "WHERE payment_operation_id=?", Integer.class, command.paymentOperationId())).isOne();
        Instant retryAt = jdbc.queryForObject("SELECT recovery_next_attempt_at FROM outpatient_payment_operation_receipt "
                + "WHERE payment_operation_id=?", java.sql.Timestamp.class, command.paymentOperationId()).toInstant();
        assertThat(retryAt).isAfter(lease.reconcileAfter());
        runReconciliationProcess("CLOSED", retryAt.plusSeconds(1), command.paymentRequestId());
        assertThat(jdbc.queryForObject(receiptSql, String.class, command.paymentOperationId())).isEqualTo("CLOSED_NOT_PAID");
        assertThat(jdbc.queryForObject("SELECT recovery_attempt_count FROM outpatient_payment_operation_receipt "
                + "WHERE payment_operation_id=?", Integer.class, command.paymentOperationId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT recovery_next_attempt_at FROM outpatient_payment_operation_receipt "
                + "WHERE payment_operation_id=?", java.sql.Timestamp.class, command.paymentOperationId())).isNull();
        assertThat(sessions.findById(held.sessionId()).orElseThrow().counts())
                .isEqualTo(new ConsultationSession.Counts(1, 0, 0, 0, 0));
    }

    private void runReconciliationProcess(String phase, Instant now, UUID request) throws Exception {
        var builder = new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx256m", "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                "com.mediflow.acceptance.fixture.PaymentReconciliationProcess", phase, now.toString(), request.toString());
        builder.environment().putAll(java.util.Map.of("TEST_DB_URL", POSTGRES.getJdbcUrl(),
                "TEST_DB_USER", POSTGRES.getUsername(), "TEST_DB_PASSWORD", POSTGRES.getPassword()));
        var log = processDirectory.resolve(phase + ".log");
        Process process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(120, TimeUnit.SECONDS))
                    .withFailMessage("Worker timed out (%s):\n%s", log, processLog(log)).isTrue();
            assertThat(process.exitValue()).withFailMessage("Worker failed (%s):\n%s", log, processLog(log)).isZero();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private String processLog(java.nio.file.Path log) throws java.io.IOException {
        var lines = java.nio.file.Files.readAllLines(log);
        return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
    }

    private PaymentLockCommand paymentCommand(OutpatientRegistrationDTO registration) {
        return new PaymentLockCommand(UUID.randomUUID(), UUID.randomUUID(), registration.registrationId(),
                registration.patientId(), "EXAM", registration.registrationId(), registration.registrationId(),
                registration.reservationRevision());
    }

    @Test
    void concurrentSameRegistrationIntent_returnsOneCreatedAndOneExactReplay() throws Exception {
        var session = registrationSession();
        var request = registrationRequest(UUID.randomUUID(), session);
        String key = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<com.mediflow.clinical.application.port.out.IdempotentCommandPort.Result<OutpatientRegistrationDTO>> create = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("race start timed out");
                return outpatientRegistrations.create(request, key);
            };
            var left = workers.submit(create);
            var right = workers.submit(create);
            start.countDown();
            var first = left.get(20, TimeUnit.SECONDS);
            var second = right.get(20, TimeUnit.SECONDS);
            assertThat(first.data()).isEqualTo(second.data());
            assertThat(java.util.List.of(first.replayed(), second.replayed())).containsExactlyInAnyOrder(false, true);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outpatient_registration WHERE session_id=?",
                    Integer.class, session.sessionId())).isOne();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM clinical_outbox_event WHERE aggregate_id=?",
                    Integer.class, first.data().registrationId())).isOne();
            assertThat(sessions.findById(session.sessionId()).orElseThrow().counts())
                    .isEqualTo(new ConsultationSession.Counts(1, 0, 0, 0, 0));
        }
    }

    private OutpatientRegistrationDTO heldRegistration() {
        var session = registrationSession();
        return outpatientRegistrations.create(registrationRequest(UUID.randomUUID(), session), UUID.randomUUID().toString()).data();
    }

    private ConsultationSession registrationSession() {
        UUID account = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        Instant now = Instant.now();
        ConsultationSession session = ConsultationSession.open(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1),
                LocalTime.of(9, 0), LocalTime.of(10, 0), List.of(), 1);
        var config = session.configuration();
        sessions.insert(session, account, "test payment lease", now);
        when(actors.current()).thenReturn(new ActorIdentity(account, staff, null, "NURSE"));
        when(patients.exists(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(schedulingAuthority.authority(org.mockito.ArgumentMatchers.eq(account),
                org.mockito.ArgumentMatchers.eq(config.departmentId()), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new SchedulingAuthoritySnapshot(account, true, true, "NURSE", staff, true,
                        config.departmentId(), true, false, 0L, now));
        when(schedulingAuthority.consultationEligibility(org.mockito.ArgumentMatchers.eq(config.doctorId()),
                org.mockito.ArgumentMatchers.eq(config.departmentId()), org.mockito.ArgumentMatchers.eq(config.roomId()),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new ConsultationEligibilitySnapshot(config.doctorId(), true, true, true,
                        config.departmentId(), config.departmentId(), true, true, config.roomId(), true, true,
                        config.departmentId(), "CONSULTATION", true, now));
        return session;
    }

    private String insertSession(CountDownLatch ready, CountDownLatch start,
            ConsultationSession session, UUID actor) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("race start timed out");
            sessions.insert(session, actor, "test race", Instant.now());
            return "COMMITTED";
        } catch (DuplicateResourceException conflict) {
            return conflict.getCode();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private RegistrationAttempt attempt(CountDownLatch ready, CountDownLatch start,
            RegistrationCreateRequest request, String key) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("race start timed out");
            return new RegistrationAttempt(outpatientRegistrations.create(request, key).data(), null);
        } catch (Throwable failure) {
            return new RegistrationAttempt(null, failure);
        }
    }

    private RegistrationCreateRequest registrationRequest(UUID patientId, ConsultationSession session) {
        return new RegistrationCreateRequest(patientId, session.sessionId(), 1, OutpatientReceptionMode.PREBOOKED,
                OutpatientVisitType.NEW_VISIT, null, "outpatient appointment");
    }

    private record RegistrationAttempt(OutpatientRegistrationDTO registration, Throwable failure) {}

    private void insertMedicalRecord(UUID recordId) {
        jdbc.update("""
                INSERT INTO medical_record(record_id, patient_id, doctor_id, department_id,
                                           examination_date, symptoms)
                VALUES (?, ?, ?, ?, ?, ?)
                """, recordId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now(), "Fever");
    }

    private int count(String table, UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE event_id = ?",
                Integer.class, eventId);
    }
}
