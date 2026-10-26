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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.application.dto.command.ConsumerInboxClaim;
import com.mediflow.clinical.application.dto.command.ActorIdentity;
import com.mediflow.clinical.application.dto.authority.SchedulingAuthoritySnapshot;
import com.mediflow.clinical.application.dto.authority.ConsultationEligibilitySnapshot;
import com.mediflow.clinical.application.port.in.AttachExternalResultUseCase;
import com.mediflow.clinical.application.port.in.ReactToLabResultUseCase;
import com.mediflow.clinical.application.port.out.ConsultationSessionRepositoryPort;
import com.mediflow.clinical.application.port.out.CurrentClinicalActorPort;
import com.mediflow.clinical.application.port.out.OutpatientRegistrationRepositoryPort;
import com.mediflow.clinical.application.port.out.PatientLookupPort;
import com.mediflow.clinical.application.port.out.SchedulingAuthorityPort;
import com.mediflow.clinical.application.dto.request.RegistrationCreateRequest;
import com.mediflow.clinical.application.dto.response.OutpatientRegistrationDTO;
import com.mediflow.clinical.domain.model.ConsultationBreak;
import com.mediflow.clinical.domain.model.ConsultationSession;
import com.mediflow.clinical.domain.model.OutpatientReceptionMode;
import com.mediflow.clinical.domain.model.OutpatientVisitType;
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
    @Autowired private OutpatientRegistrationRepositoryPort registrations;
    @Autowired private OutpatientRegistrationApplicationService outpatientRegistrations;

    @MockBean
    private AttachExternalResultUseCase attachments;

    @MockBean private CurrentClinicalActorPort actors;
    @MockBean private SchedulingAuthorityPort schedulingAuthority;
    @MockBean private PatientLookupPort patients;

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
