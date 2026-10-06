package com.mediflow.surgery.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.surgery.application.port.in.ApplySurgeryAuthorityInvalidationUseCase;
import com.mediflow.surgery.application.port.in.QuerySurgeryAuthorityInvalidationsUseCase;
import com.mediflow.surgery.application.port.in.ReceiveSurgeryAuthorityChangeUseCase;
import com.mediflow.surgery.application.port.in.RecordSurgeryAuthorityInvalidationRetryUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import com.mediflow.surgery.infrastructure.messaging.SurgeryAuthorityChangeDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import com.mediflow.surgery.messaging.consumer.SurgeryAuthorityChangeConsumer;
import com.mediflow.surgery.infrastructure.config.SurgeryAuthorityConsumerConfiguration;
import com.mediflow.surgery.infrastructure.config.SurgeryAuthorityInvalidationWorker;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties = {"mediflow.jwt.secret=surgery-authority-test-secret-at-least-32-bytes",
        "mediflow.features.surgery.enabled=true", "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "mediflow.surgery.messaging.consumers.enabled=true", "mediflow.surgery.messaging.organization-authority.enabled=true",
        "mediflow.surgery.messaging.organization-authority.initial-delay-ms=600000",
        "mediflow.surgery.messaging.consumers.pending-poll-ms=600000", "spring.rabbitmq.publisher-confirm-type=simple"})
class SurgeryAuthorityInvalidationIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }
    private static final Instant NOW = Instant.parse("2026-10-05T02:01:00Z");
    private static final UUID ROOM = UUID.fromString("01000000-0000-0000-0000-000000000001");
    private static final UUID STAFF = UUID.fromString("02000000-0000-0000-0000-000000000001");
    private static final SurgeryAuditActor ACTOR = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
    @Autowired SurgeryCaseRepositoryPort cases;
    @Autowired SurgeryScheduleRepositoryPort schedules;
    @Autowired SurgeryResourceReservationPort resources;
    @Autowired ReceiveSurgeryAuthorityChangeUseCase receive;
    @Autowired QuerySurgeryAuthorityInvalidationsUseCase query;
    @Autowired ApplySurgeryAuthorityInvalidationUseCase apply;
    @Autowired RecordSurgeryAuthorityInvalidationRetryUseCase retry;
    @Autowired SurgeryAuthorityChangeDecoder decoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PlatformTransactionManager transactions;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitListenerEndpointRegistry listeners;
    // Real clock during context startup; deterministic stub only after scheduling has initialized.
    @SpyBean SurgeryClockPort clock;

    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE surgery_case, surgery_inbox, surgery_inbox_semantic_mutex CASCADE");
        when(clock.now()).thenReturn(NOW);
        new RabbitAdmin(rabbit).purgeQueue(SurgeryAuthorityChangeConsumer.QUEUE, false);
        new RabbitAdmin(rabbit).purgeQueue(SurgeryAuthorityConsumerConfiguration.DLQ, false);
        listeners.start();
    }
    @org.junit.jupiter.api.AfterEach void stopListeners() { listeners.stop(); }

    @Test void actualRabbitAckLeavesDurableWorkThenFreshWorkerInvalidatesAndDuplicatesAreHarmless() throws Exception {
        var fixture = persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        byte[] bytes = fixture("room");
        send(bytes); send(bytes);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(query.findDue(20)).hasSize(1));
        drain();
        assertThat(count("surgery_inbox")).isEqualTo(1); assertThat(count("surgery_authority_change")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payload FROM surgery_inbox", byte[].class)).containsExactly(bytes);
        assertThat(status(fixture)).isEqualTo(SurgeryStatus.SCHEDULED);
        new SurgeryAuthorityInvalidationWorker(query, apply, retry, 20).poll();
        assertThat(status(fixture)).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS); assertResources(fixture, "RELEASED");
        assertThat(rabbit.receive(SurgeryAuthorityConsumerConfiguration.DLQ, 200)).isNull();
    }
    @Test void actualRabbitMalformedPayloadGoesToDlqWithoutAnyInboxOrWork() {
        byte[] malformed = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        send(malformed);
        var dead = rabbit.receive(SurgeryAuthorityConsumerConfiguration.DLQ, 10000);
        assertThat(dead).isNotNull(); assertThat(dead.getBody()).containsExactly(malformed);
        assertThat(count("surgery_inbox")).isZero(); assertThat(count("surgery_authority_invalidation")).isZero();
    }
    @Test void actualRabbitSourceConflictIsQuarantinedAndDeadLetteredWithoutOverwritingWork() throws Exception {
        persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        send(fixture("room"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(query.findDue(20)).hasSize(1));
        var conflict = root("room"); conflict.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode)conflict.path("payload")).put("reason", "Contradictory content");
        byte[] bytes = mapper.writeValueAsBytes(conflict); send(bytes);
        var dead = rabbit.receive(SurgeryAuthorityConsumerConfiguration.DLQ, 10000);
        assertThat(dead).isNotNull(); assertThat(dead.getBody()).containsExactly(bytes);
        assertThat(count("surgery_authority_change")).isEqualTo(1); assertThat(count("surgery_authority_invalidation")).isEqualTo(1);
    }
    @Test void actualRabbitStorageFailureRetriesThreeTimesThenDlqCanBeReplayedAfterRecovery() throws Exception {
        persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        // Nontransactional PG sequence counts attempts even when each listener transaction rolls back.
        jdbc.execute("CREATE SEQUENCE authority_job_attempts");
        jdbc.execute("CREATE FUNCTION fail_rabbit_authority_job() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM nextval('authority_job_attempts'); RAISE EXCEPTION 'test storage failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_rabbit_authority_job BEFORE INSERT ON surgery_authority_invalidation FOR EACH ROW EXECUTE FUNCTION fail_rabbit_authority_job()");
        byte[] bytes = fixture("room");
        try {
            send(bytes);
            var dead = rabbit.receive(SurgeryAuthorityConsumerConfiguration.DLQ, 10000);
            assertThat(dead).isNotNull(); assertThat(dead.getBody()).containsExactly(bytes);
            assertThat(jdbc.queryForObject("SELECT last_value FROM authority_job_attempts", Integer.class)).isEqualTo(3);
            assertThat(count("surgery_inbox")).isZero(); assertThat(count("surgery_authority_change")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_rabbit_authority_job ON surgery_authority_invalidation");
            jdbc.execute("DROP FUNCTION fail_rabbit_authority_job()"); jdbc.execute("DROP SEQUENCE authority_job_attempts");
        }
        send(bytes);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(query.findDue(20)).hasSize(1));
        drain(); assertThat(count("surgery_inbox")).isEqualTo(1);
    }
    @Test void roomEventCapturesOnlyRelatedPrestartCasesThenReleasesExactBooking() throws Exception {
        var scheduled = persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        var ready = persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, false);
        var other = persist(UUID.randomUUID(), UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        assertThat(receive.receive(command("room"))).isEqualTo(ReceiveSurgeryAuthorityChangeUseCase.Outcome.APPLIED);
        assertThat(status(scheduled)).isEqualTo(SurgeryStatus.SCHEDULED); // intake only persists jobs
        assertThat(query.findDue(20)).hasSize(2);
        for (var candidate : query.findDue(20)) assertThat(apply.apply(candidate)).isTrue();
        assertThat(status(scheduled)).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(status(ready)).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(status(other)).isEqualTo(SurgeryStatus.SCHEDULED);
        assertResources(scheduled, "RELEASED"); assertResources(other, "RESERVED");
        assertThat(count("surgery_readiness_snapshot")).isEqualTo(3);
        assertThat(count("surgery_outbox")).isZero(); // no invented notification/billing event
        assertThat(jdbc.queryForMap("SELECT actor_type, system_producer, correlation_id FROM surgery_status_history "
                + "WHERE surgery_case_id = ? AND reason = 'ORGANIZATION_AUTHORITY_CHANGED'", scheduled.value().getSurgeryCaseId()))
                .containsEntry("actor_type", "SYSTEM").containsEntry("system_producer", "organization-service")
                .containsEntry("correlation_id", command("room").correlationId());
        assertThat(query.findDue(20)).isEmpty();
    }
    @Test void staffCapabilityTargetsExactStaffAndRoleNotGenericDoctor() throws Exception {
        var affected = persist(UUID.randomUUID(), STAFF, SurgeryTeamRole.PRIMARY_SURGEON, true);
        var differentRole = persist(UUID.randomUUID(), STAFF, SurgeryTeamRole.ASSISTANT_SURGEON, true);
        persist(UUID.randomUUID(), UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        receive.receive(command("staff"));
        assertThat(query.findDue(20)).hasSize(1);
        assertThat(apply.apply(query.findDue(20).getFirst())).isTrue();
        assertResources(affected, "RELEASED"); assertResources(differentRole, "RESERVED");
    }
    @Test void eventReplayAndSemanticNewEventDoNotReopenCompletedJob() throws Exception {
        persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        var command = command("room");
        assertThat(receive.receive(command)).isEqualTo(ReceiveSurgeryAuthorityChangeUseCase.Outcome.APPLIED);
        var candidate = query.findDue(20).getFirst(); assertThat(apply.apply(candidate)).isTrue();
        assertThat(receive.receive(command)).isEqualTo(ReceiveSurgeryAuthorityChangeUseCase.Outcome.REPLAYED);
        var replay = root("room"); replay.put("eventId", UUID.randomUUID().toString()); replay.put("correlationId", UUID.randomUUID().toString());
        assertThat(receive.receive(decode(replay))).isEqualTo(ReceiveSurgeryAuthorityChangeUseCase.Outcome.REPLAYED);
        assertThat(count("surgery_authority_change")).isEqualTo(1);
        assertThat(count("surgery_authority_invalidation")).isEqualTo(1);
        assertThat(count("surgery_inbox")).isEqualTo(2);
        assertThat(query.findDue(20)).isEmpty(); assertThat(apply.apply(candidate)).isFalse();
    }
    @Test void sameRevisionChangedContentQuarantinesWithoutReplacingEvidence() throws Exception {
        persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        receive.receive(command("room"));
        var changed = root("room"); changed.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode)changed.path("payload")).put("reason", "Contradictory source content");
        assertThat(receive.receive(decode(changed))).isEqualTo(ReceiveSurgeryAuthorityChangeUseCase.Outcome.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_inbox WHERE status = 'QUARANTINED'", Integer.class)).isEqualTo(1);
        assertThat(count("surgery_authority_invalidation")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reason FROM surgery_authority_change", String.class)).isEqualTo("Room maintenance decision");
    }
    @Test void conflictingSameEventIdentityCannotMutateOriginalWork() throws Exception {
        persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        receive.receive(command("room"));
        var changed = root("room"); ((ObjectNode)changed.path("payload")).put("revision", 3);
        assertThat(receive.receive(decode(changed))).isEqualTo(ReceiveSurgeryAuthorityChangeUseCase.Outcome.CONFLICT);
        assertThat(count("surgery_authority_change")).isEqualTo(1);
        assertThat(query.findDue(20)).hasSize(1);
    }
    @Test void startWinnerPreservesInUseAndSupersedesOldJob() throws Exception {
        var fixture = persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        receive.receive(command("room")); var job = query.findDue(20).getFirst();
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            var value = cases.lockById(job.surgeryCaseId()).orElseThrow();
            resources.markInUse(job.surgeryCaseId(), fixture.schedule().scheduleId(), 1, NOW);
            value.start(value.getReadinessSnapshot(), ACTOR, "authority-start-test", NOW); cases.save(value, 3);
        });
        assertThat(apply.apply(job)).isFalse(); retry.defer(job, "StaleWorker");
        assertResources(fixture, "IN_USE"); assertThat(status(fixture)).isEqualTo(SurgeryStatus.IN_PROGRESS);
        assertThat(jdbc.queryForObject("SELECT status FROM surgery_authority_invalidation", String.class)).isEqualTo("SUPERSEDED");
        assertThat(query.findDue(20)).isEmpty();
    }
    @Test void replacementReadinessIsNotInvalidatedByStalePinnedWork() throws Exception {
        var fixture = persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, false);
        receive.receive(command("room")); var job = query.findDue(20).getFirst();
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            var value = cases.lockById(job.surgeryCaseId()).orElseThrow();
            value.invalidateReadiness(ACTOR, "replacement-test", NOW, "TEST_REPLACEMENT");
            value.markReady(snapshot(value, fixture.schedule(), NOW), ACTOR, "replacement-test"); cases.save(value, 2);
        });
        assertThat(apply.apply(job)).isFalse();
        assertThat(status(fixture)).isEqualTo(SurgeryStatus.READY);
        assertThat(count("surgery_readiness_snapshot")).isEqualTo(2);
        assertThat(query.findDue(20)).isEmpty();
    }
    @Test void atomicAuditFailureRollsBackReleaseAndDurableRetryRecovers() throws Exception {
        var fixture = persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        receive.receive(command("room")); var job = query.findDue(20).getFirst();
        jdbc.execute("""
                CREATE FUNCTION fail_authority_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.reason = 'ORGANIZATION_AUTHORITY_CHANGED' THEN RAISE EXCEPTION 'test-only audit failure'; END IF;
                RETURN NEW; END $$
                """);
        jdbc.execute("CREATE TRIGGER fail_authority_audit BEFORE INSERT ON surgery_status_history FOR EACH ROW EXECUTE FUNCTION fail_authority_audit()");
        try {
            assertThatThrownBy(() -> apply.apply(job)).isInstanceOf(RuntimeException.class);
            assertThat(status(fixture)).isEqualTo(SurgeryStatus.SCHEDULED); assertResources(fixture, "RESERVED");
            assertThat(jdbc.queryForObject("SELECT status FROM surgery_authority_invalidation", String.class)).isEqualTo("PENDING");
            retry.defer(job, "AuditWriteFailed"); assertThat(query.findDue(20)).isEmpty();
            assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM surgery_authority_invalidation", Timestamp.class).toInstant()).isEqualTo(NOW.plusSeconds(5));
        } finally {
            jdbc.execute("DROP TRIGGER fail_authority_audit ON surgery_status_history"); jdbc.execute("DROP FUNCTION fail_authority_audit()");
        }
        when(clock.now()).thenReturn(NOW.plusSeconds(5));
        assertThat(query.findDue(20)).containsExactly(job); assertThat(apply.apply(job)).isTrue(); assertResources(fixture, "RELEASED");
    }
    @Test void twoWorkersCommitExactlyOneInvalidation() throws Exception {
        persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        receive.receive(command("room")); var job = query.findDue(20).getFirst(); var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return apply.apply(job); });
            var b = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return apply.apply(job); });
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_status_history WHERE reason = 'ORGANIZATION_AUTHORITY_CHANGED'", Integer.class)).isEqualTo(1);
    }
    @Test void sourceRevisionsDeliveredOutOfOrderOnlyInvalidateOnceAndNeverGrantPermission() throws Exception {
        var value = persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        var newest = root("room"); newest.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode)newest.path("payload")).put("revision", 3);
        receive.receive(decode(newest)); receive.receive(command("room"));
        assertThat(count("surgery_authority_change")).isEqualTo(2); assertThat(query.findDue(20)).hasSize(2);
        long effects = query.findDue(20).stream().filter(apply::apply).count();
        assertThat(effects).isEqualTo(1); assertThat(status(value)).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertResources(value, "RELEASED"); assertThat(query.findDue(20)).isEmpty();
    }
    @Test void alreadyStartedCaseDoesNotReceivePrestartJobEvenForLateHint() throws Exception {
        var fixture = persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            var value = cases.lockById(fixture.value().getSurgeryCaseId()).orElseThrow();
            resources.markInUse(value.getSurgeryCaseId(), fixture.schedule().scheduleId(), 1, NOW);
            value.start(value.getReadinessSnapshot(), ACTOR, "already-started-test", NOW); cases.save(value, 3);
        });
        receive.receive(command("room"));
        assertThat(count("surgery_authority_change")).isEqualTo(1); assertThat(query.findDue(20)).isEmpty();
        assertResources(fixture, "IN_USE");
    }
    @Test void forgedPinCannotClaimWorkAndInconsistentCurrentRevisionCannotReleaseBooking() throws Exception {
        var fixture = persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        receive.receive(command("room")); var candidate = query.findDue(20).getFirst();
        var forged = new QuerySurgeryAuthorityInvalidationsUseCase.Candidate(candidate.eventId(), candidate.surgeryCaseId(),
                candidate.readinessSnapshotId(), UUID.randomUUID(), candidate.scheduleRevision());
        assertThat(apply.apply(forged)).isFalse(); assertThat(query.findDue(20)).containsExactly(candidate);
        jdbc.update("UPDATE surgery_schedule SET revision = 2 WHERE schedule_id = ?", candidate.scheduleId());
        assertThatThrownBy(() -> apply.apply(candidate)).isInstanceOf(com.mediflow.surgery.application.exception.SurgeryRevisionConflictException.class);
        assertResources(fixture, "RESERVED"); assertThat(status(fixture)).isEqualTo(SurgeryStatus.SCHEDULED);
        retry.defer(candidate, "SurgeryRevisionConflictException"); assertThat(query.findDue(20)).isEmpty();
    }
    @Test void retryBackoffIsDurableBoundedAndCompletedWorkCannotReopen() throws Exception {
        persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        receive.receive(command("room")); var candidate = query.findDue(20).getFirst(); Instant at = NOW;
        for (int attempt = 0; attempt < 9; attempt++) {
            when(clock.now()).thenReturn(at); retry.defer(candidate, "TemporaryStorageFailure");
            Instant next = jdbc.queryForObject("SELECT next_attempt_at FROM surgery_authority_invalidation", Timestamp.class).toInstant();
            assertThat(Duration.between(at, next).toSeconds()).isEqualTo(Math.min(300, 5L << Math.min(6, attempt)));
            assertThat(query.findDue(20)).isEmpty(); at = next;
        }
        when(clock.now()).thenReturn(at); assertThat(apply.apply(candidate)).isTrue();
        retry.defer(candidate, "LateFailedWorker"); assertThat(query.findDue(20)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM surgery_authority_invalidation", Integer.class)).isEqualTo(9);
    }
    @Test void completionMarkerFailureRollsBackAlreadyWrittenCaseAuditAndRelease() throws Exception {
        var fixture = persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        receive.receive(command("room")); var candidate = query.findDue(20).getFirst();
        jdbc.execute("CREATE FUNCTION fail_authority_finish() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status = 'APPLIED' THEN RAISE EXCEPTION 'test finish failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_authority_finish BEFORE UPDATE ON surgery_authority_invalidation FOR EACH ROW EXECUTE FUNCTION fail_authority_finish()");
        try {
            assertThatThrownBy(() -> apply.apply(candidate)).isInstanceOf(RuntimeException.class);
            assertThat(status(fixture)).isEqualTo(SurgeryStatus.SCHEDULED); assertResources(fixture, "RESERVED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_status_history WHERE reason = 'ORGANIZATION_AUTHORITY_CHANGED'", Integer.class)).isZero();
            assertThat(query.findDue(20)).containsExactly(candidate);
        } finally {
            jdbc.execute("DROP TRIGGER fail_authority_finish ON surgery_authority_invalidation"); jdbc.execute("DROP FUNCTION fail_authority_finish()");
        }
        assertThat(apply.apply(candidate)).isTrue();
    }
    @Test void intakeJobWriteFailureRollsBackInboxAndEvidenceBeforeAck() throws Exception {
        persist(ROOM, UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON, true);
        jdbc.execute("CREATE FUNCTION fail_authority_job() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test-only job failure'; END $$");
        jdbc.execute("CREATE TRIGGER fail_authority_job BEFORE INSERT ON surgery_authority_invalidation FOR EACH ROW EXECUTE FUNCTION fail_authority_job()");
        try {
            assertThatThrownBy(() -> receive.receive(command("room"))).isInstanceOf(RuntimeException.class);
            assertThat(count("surgery_inbox")).isZero(); assertThat(count("surgery_authority_change")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_authority_job ON surgery_authority_invalidation"); jdbc.execute("DROP FUNCTION fail_authority_job()");
        }
        assertThat(receive.receive(command("room"))).isEqualTo(ReceiveSurgeryAuthorityChangeUseCase.Outcome.APPLIED);
        assertThat(query.findDue(20)).hasSize(1);
    }

    private Fixture persist(UUID room, UUID staff, SurgeryTeamRole role, boolean scheduled) {
        return new TransactionTemplate(transactions).execute(ignored -> {
            var value = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                    new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                    UUID.randomUUID(), UUID.randomUUID(), ACTOR.verifiedStaffId(), "AUTHORITY-TEST", "Test indication",
                    SurgeryPriority.ROUTINE, NOW.minusSeconds(60), ACTOR, "authority-test");
            cases.save(value, -1); value.beginPreop(ACTOR, "authority-test", NOW.minusSeconds(50)); cases.save(value, 0);
            // Different slots allow fixture cases to share the exact resource without violating booking rules.
            long offset = jdbc.queryForObject("SELECT count(*) FROM surgery_case", Long.class) * 1000;
            var schedule = new SurgerySchedule(UUID.randomUUID(), value.getSurgeryCaseId(), 1, room,
                    NOW.plusSeconds(offset), NOW.plusSeconds(offset + 100), List.of(new SurgeryTeamAssignment(staff, role)));
            schedules.saveDraft(schedule, 0, NOW.minusSeconds(40));
            value.markReady(snapshot(value, schedule), ACTOR, "authority-test"); cases.save(value, 1);
            if (scheduled) { resources.reserve(schedule, NOW.minusSeconds(20)); value.finalizeSchedule(ACTOR, "authority-test", NOW.minusSeconds(20)); cases.save(value, 2); }
            return new Fixture(value, schedule);
        });
    }
    // Only test prerequisites; this does NOT certify clinical readiness evaluation or START policy.
    private ReadinessSnapshot snapshot(SurgeryCase value, SurgerySchedule schedule) {
        return snapshot(value, schedule, NOW.minusSeconds(30));
    }
    private ReadinessSnapshot snapshot(SurgeryCase value, SurgerySchedule schedule, Instant evaluatedAt) {
        return ReadinessSnapshot.evaluate(UUID.randomUUID(), value.getSurgeryCaseId(), true, true, true, true, true, true, true,
                evaluatedAt, Arrays.stream(SurgeryDependencyType.values()).map(type -> type == SurgeryDependencyType.SCHEDULE
                        ? new SurgeryDependencyRevision(type, schedule.scheduleId(), schedule.revision())
                        : new SurgeryDependencyRevision(type, UUID.randomUUID(), 1)).toList(), NOW.plusSeconds(600));
    }
    private SurgeryStatus status(Fixture fixture) { return cases.findById(fixture.value().getSurgeryCaseId()).orElseThrow().getStatus(); }
    private void assertResources(Fixture fixture, String status) {
        assertThat(jdbc.queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id = ?", String.class,
                fixture.value().getSurgeryCaseId())).containsExactly(status, status);
    }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private ObjectNode root(String kind) throws Exception {
        return (ObjectNode)mapper.readTree(fixture(kind));
    }
    private byte[] fixture(String kind) throws Exception {
        return Files.readAllBytes(Path.of("../organization-service/src/test/resources/contracts/surgery-authority-v1/event." + kind + ".changed.json"));
    }
    private ReceiveSurgeryAuthorityChangeUseCase.Command command(String kind) throws Exception {
        return decoder.decode(ReceiveSurgeryAuthorityChangeUseCase.EVENT_TYPE, fixture(kind), NOW);
    }
    private ReceiveSurgeryAuthorityChangeUseCase.Command decode(ObjectNode root) throws Exception {
        return decoder.decode(ReceiveSurgeryAuthorityChangeUseCase.EVENT_TYPE, mapper.writeValueAsBytes(root), NOW);
    }
    private void send(byte[] bytes) {
        var properties = new MessageProperties(); properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbit.invoke(operations -> {
            operations.send("mediflow.events", ReceiveSurgeryAuthorityChangeUseCase.EVENT_TYPE, new Message(bytes, properties));
            operations.waitForConfirmsOrDie(5000); return null;
        });
    }
    private void drain() {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(new RabbitAdmin(rabbit)
                .getQueueInfo(SurgeryAuthorityChangeConsumer.QUEUE).getMessageCount()).isZero());
        listeners.stop(); // waits for in-flight commits/ACKs, not just queue-ready count
    }
    private record Fixture(SurgeryCase value, SurgerySchedule schedule) {}
}
