package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.application.service.AuthorizedSurgeryConsentRevocationService;
import com.mediflow.surgery.domain.model.*;
import com.mediflow.surgery.support.SurgeryScheduledFixture;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real owned kernels/locks/audit/HELD writes. Authority double is not a production legal policy. */
@Testcontainers
@SpringBootTest(properties = {"mediflow.features.surgery.enabled=true",
        "mediflow.jwt.secret=consent-revocation-pg-secret-at-least-32-bytes",
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
class SurgeryAuthorizedConsentRevocationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", PG::getJdbcUrl);
        r.add("spring.datasource.username", PG::getUsername);
        r.add("spring.datasource.password", PG::getPassword);
    }
    @Autowired JdbcTemplate db;
    @Autowired SurgeryCaseRepositoryPort cases;
    @Autowired SurgeryConsentRepositoryPort consents;
    @Autowired ManageSurgeryConsentUseCase kernel;
    @Autowired SurgeryUnitOfWorkPort transactions;
    @Autowired SurgeryResourceReservationPort reservations;
    @Autowired PlatformTransactionManager manager;
    @MockBean SurgeryClockPort clock;
    @SpyBean SurgeryCommandReceiptPort receipts;
    private final SurgeryConsentRevocationAuthorityPort authority = mock(SurgeryConsentRevocationAuthorityPort.class);
    private final AtomicReference<Instant> now = new AtomicReference<>();
    private AuthorizedSurgeryConsentRevocationService service;
    private SurgeryScheduledFixture.Fixture fixture;
    private SurgeryConsentRecord consent;
    private SurgeryAuditActor actor;
    private ManageSurgeryConsentUseCase.RevokeCommand command;

    @BeforeEach void prepare() {
        db.execute("TRUNCATE surgery_case CASCADE");
        reset(authority, clock, receipts);
        actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        fixture = SurgeryScheduledFixture.seed(db, UUID.randomUUID(), UUID.randomUUID(), actor.verifiedStaffId(),
                Instant.now().plusSeconds(600), Instant.now().plusSeconds(1200));
        now.set(Instant.now().truncatedTo(ChronoUnit.MICROS).plusSeconds(3));
        consent = SurgeryConsentRecord.sign(UUID.randomUUID(), fixture.caseId(), SurgeryConsentType.SURGERY,
                UUID.randomUUID(), SurgeryConsentSignerType.PATIENT, UUID.randomUUID(), actor, now.get().minusSeconds(1), "signed");
        new TransactionTemplate(manager).executeWithoutResult(ignored -> consents.save(consent));
        command = new ManageSurgeryConsentUseCase.RevokeCommand(fixture.caseId(), consent.consentId(), 3,
                "Patient withdrew consent", "revoke-key", actor, "revoke-correlation");
        when(clock.now()).thenAnswer(call -> now.get());
        when(authority.approve(any(), any(), any(), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new SurgeryPreopAuthorityPort.Approval(call.getArgument(3), now.get(), now.get().plusSeconds(20));
        });
        service = new AuthorizedSurgeryConsentRevocationService(cases, consents, kernel, authority, clock, transactions);
    }

    @Test void revoke_commitsConsentAuditInvalidationExactReleaseReceiptAndHeldFact_once() {
        var outcome = service.revoke(command);
        assertThat(consents.findById(consent.consentId()).orElseThrow().isActive()).isFalse();
        assertThat(consents.findById(consent.consentId()).orElseThrow().auditHistory()).hasSize(2);
        assertThat(caseStatus()).isEqualTo("PREOP_IN_PROGRESS");
        assertThat(db.queryForObject("SELECT readiness_snapshot_id FROM surgery_case WHERE surgery_case_id=?",
                UUID.class, fixture.caseId())).isNull();
        assertThat(bookings()).containsOnly("RELEASED");
        assertThat(count("surgery_command_receipt")).isOne();
        assertThat(count("surgery_care_event_outbox")).isOne();
        assertThat(db.queryForObject("SELECT delivery_status FROM surgery_care_event_outbox WHERE surgery_case_id=?",
                String.class, fixture.caseId())).isEqualTo("HELD");
        assertThat(service.revoke(command)).isEqualTo(outcome.asReplay());
        assertThat(count("surgery_command_receipt")).isOne();
        assertThat(count("surgery_care_event_outbox")).isOne();
        verify(authority, times(2)).approve(any(), any(), any(), anyString());
    }

    @Test void revoke_sourceDenial_keepsEveryOwnedRowUnchanged() {
        doThrow(new AccessDeniedException("Test-only authority denial")).when(authority).approve(any(), any(), any(), anyString());
        assertThatThrownBy(() -> service.revoke(command)).isInstanceOf(AccessDeniedException.class);
        assertUnchanged();
    }

    @Test void revoke_receiptFailure_rollsBackAllEffectsThenSameCommandRecovers() {
        doThrow(new IllegalStateException("Injected receipt failure"))
                .when(receipts).complete(any(), any(), anyString(), any(), any());
        assertThatThrownBy(() -> service.revoke(command))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertUnchanged();
        reset(receipts);
        assertThat(service.revoke(command).replayed()).isFalse();
        assertThat(count("surgery_care_event_outbox")).isOne();
    }

    @Test void revoke_proofExpiresAfterReceiptWrite_rollsBackAllEffectsThenRecovers() {
        doAnswer(call -> {
            call.callRealMethod();
            now.updateAndGet(t -> t.plusSeconds(20));
            return null;
        }).when(receipts).complete(any(), any(), anyString(), any(), any());
        assertThatThrownBy(() -> service.revoke(command)).isInstanceOf(UpstreamUnavailableException.class);
        assertUnchanged();
        reset(receipts);
        assertThat(service.revoke(command).replayed()).isFalse();
        assertThat(count("surgery_command_receipt")).isOne();
    }

    @Test void revoke_authorizedRetryAfterRestart_preservesOriginalTimeAndCannotBypassFreshDenial() {
        var original = service.revoke(command);
        now.updateAndGet(t -> t.plusSeconds(120));
        var reconstructed = new AuthorizedSurgeryConsentRevocationService(cases, consents, kernel, authority, clock, transactions);
        assertThat(reconstructed.revoke(command)).isEqualTo(original.asReplay());
        doThrow(new AccessDeniedException("Current authority denied")).when(authority).approve(any(), any(), any(), anyString());
        assertThatThrownBy(() -> reconstructed.revoke(command)).isInstanceOf(AccessDeniedException.class);
        assertThat(count("surgery_command_receipt")).isOne();
        assertThat(count("surgery_care_event_outbox")).isOne();
    }

    @Test void revoke_changedIntentSameKey_conflictsWithoutReplacingOriginalAudit() {
        service.revoke(command);
        var before = consents.findById(consent.consentId()).orElseThrow();
        var changed = new ManageSurgeryConsentUseCase.RevokeCommand(fixture.caseId(), consent.consentId(), 3,
                "Different reason", command.idempotencyKey(), actor, "new-delivery");
        assertThatThrownBy(() -> service.revoke(changed)).isInstanceOf(SurgeryRevisionConflictException.class);
        assertThat(consents.findById(consent.consentId()).orElseThrow()).isEqualTo(before);
        assertThat(count("surgery_command_receipt")).isOne();
        assertThat(count("surgery_care_event_outbox")).isOne();
    }

    @Test void revoke_startWinsDuringAuthorityPreflight_doesNotReleaseInUseOrAppendRevocation() throws Exception {
        var observed = new CountDownLatch(1);
        var continueRevoke = new CountDownLatch(1);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            observed.countDown();
            assertThat(continueRevoke.await(5, TimeUnit.SECONDS)).isTrue();
            return new SurgeryPreopAuthorityPort.Approval(call.getArgument(3), now.get(), now.get().plusSeconds(20));
        }).when(authority).approve(any(), any(), any(), anyString());
        try (var pool = Executors.newSingleThreadExecutor()) {
            var revocation = pool.submit(() -> service.revoke(command));
            try {
                assertThat(observed.await(5, TimeUnit.SECONDS)).isTrue();
                new TransactionTemplate(manager).executeWithoutResult(ignored -> {
                    var value = cases.lockById(fixture.caseId()).orElseThrow();
                    value.start(value.getReadinessSnapshot(), actor, "test-only-start-winner", now.get());
                    reservations.markInUse(fixture.caseId(), fixture.scheduleId(), 1, now.get());
                    cases.save(value, 3);
                });
            } finally { continueRevoke.countDown(); }
            assertThatThrownBy(() -> revocation.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(SurgeryRevisionConflictException.class);
        }
        assertThat(caseStatus()).isEqualTo("IN_PROGRESS");
        assertThat(bookings()).containsOnly("IN_USE");
        assertThat(consents.findById(consent.consentId()).orElseThrow()).isEqualTo(consent);
        assertThat(count("surgery_command_receipt")).isZero();
        assertThat(count("surgery_care_event_outbox")).isZero();
    }

    @Test void revoke_ambientTransaction_isSuspendedAndNewWriteCommitsIndependently() {
        new TransactionTemplate(manager).executeWithoutResult(outer -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            service.revoke(command);
            outer.setRollbackOnly();
        });
        assertThat(consents.findById(consent.consentId()).orElseThrow().isActive()).isFalse();
        assertThat(count("surgery_command_receipt")).isOne();
        assertThat(count("surgery_care_event_outbox")).isOne();
    }

    @Test void revoke_twoAuthorizedWorkers_oneAtomicWinnerAndLoserCanReplay() throws Exception {
        var bothObserved = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        doAnswer(call -> {
            bothObserved.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return new SurgeryPreopAuthorityPort.Approval(call.getArgument(3), now.get(), now.get().plusSeconds(20));
        }).when(authority).approve(any(), any(), any(), anyString());
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Object> attempt = () -> {
                try { return service.revoke(command); }
                catch (SurgeryRevisionConflictException conflict) { return conflict; }
            };
            var one = pool.submit(attempt);
            var two = pool.submit(attempt);
            try { assertThat(bothObserved.await(5, TimeUnit.SECONDS)).isTrue(); }
            finally { release.countDown(); }
            var results = java.util.List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
            assertThat(results).filteredOn(value -> value instanceof com.mediflow.surgery.application.dto.SurgeryCommandOutcome).hasSize(1);
            assertThat(results).filteredOn(value -> value instanceof SurgeryRevisionConflictException).hasSize(1);
        }
        assertThat(service.revoke(command).replayed()).isTrue();
        assertThat(count("surgery_command_receipt")).isOne();
        assertThat(count("surgery_care_event_outbox")).isOne();
        assertThat(consents.findById(consent.consentId()).orElseThrow().auditHistory()).hasSize(2);
        assertThat(bookings()).containsOnly("RELEASED");
    }

    private void assertUnchanged() {
        assertThat(consents.findById(consent.consentId()).orElseThrow()).isEqualTo(consent);
        assertThat(caseStatus()).isEqualTo("SCHEDULED");
        assertThat(bookings()).containsOnly("RESERVED");
        assertThat(count("surgery_command_receipt")).isZero();
        assertThat(count("surgery_care_event_outbox")).isZero();
        assertThat(db.queryForObject("SELECT revision FROM surgery_case WHERE surgery_case_id=?", Long.class, fixture.caseId())).isEqualTo(3);
    }
    private String caseStatus() {
        return db.queryForObject("SELECT status FROM surgery_case WHERE surgery_case_id=?", String.class, fixture.caseId());
    }
    private java.util.List<String> bookings() {
        return db.queryForList("SELECT status FROM surgery_resource_reservation WHERE surgery_case_id=?", String.class, fixture.caseId());
    }
    private int count(String ownedTable) {
        // Table names are constants in this isolated owned test, never external request input.
        return db.queryForObject("SELECT count(*) FROM " + ownedTable + " WHERE surgery_case_id=?", Integer.class, fixture.caseId());
    }
}
