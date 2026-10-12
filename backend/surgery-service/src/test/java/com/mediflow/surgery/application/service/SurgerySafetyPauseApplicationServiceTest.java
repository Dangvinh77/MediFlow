package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryPreopAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgerySafetyPauseAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgerySafetyPauseStorePort;
import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySafetyPause;
import com.mediflow.surgery.domain.model.SurgerySafetyPhase;
import com.mediflow.surgery.support.SurgerySafetyPauseFixture;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Application-level fencing tests for the private, authority-backed safety pause recorder. */
@ExtendWith(MockitoExtension.class)
class SurgerySafetyPauseApplicationServiceTest {
    private static final Instant NOW = SurgerySafetyPauseFixture.NOW;
    private static final UUID CASE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SCHEDULE_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID REQUEST_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PATIENT_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID DEPARTMENT_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID ACCOUNT_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final UUID STAFF_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final String CORRELATION = "safety-pause-application-test";

    @Mock SurgeryCaseRepositoryPort cases;
    @Mock SurgerySafetyPauseStorePort store;
    @Mock SurgerySafetyPauseAuthorityPort authority;
    @Mock SurgeryClockPort clock;
    @Mock SurgeryUnitOfWorkPort transactions;

    private SurgerySafetyPauseApplicationService service;

    @BeforeEach
    void setUp() {
        service = new SurgerySafetyPauseApplicationService(cases, store, authority, clock, transactions);
        lenient().when(clock.now()).thenReturn(NOW);
        lenient().doAnswer(invocation -> run(invocation.getArgument(0))).when(transactions).read(any());
        lenient().doAnswer(invocation -> run(invocation.getArgument(0))).when(transactions).outside(any());
        lenient().doAnswer(invocation -> run(invocation.getArgument(0))).when(transactions).write(any());
        lenient().when(store.append(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(authority.approve(any(), any(), anyString()))
                .thenAnswer(invocation -> approval(invocation.getArgument(2), NOW, NOW.plusSeconds(60)));
    }

    @Test
    void record_validEvidence_authorizesOutsideWriteAndRecordsClockOwnedTimestamp() {
        SurgerySafetyPause proposal = proposal(0L);
        SurgeryCase current = surgeryCase(proposal, proposal.expectedCaseRevision());
        stubCase(current);

        SurgerySafetyPause recorded = service.record(proposal);

        assertThat(recorded.recordedAt()).isEqualTo(NOW);
        assertThat(recorded.recordedAt()).isNotEqualTo(proposal.recordedAt());
        verify(authority).approve(any(), eq(proposal), anyString());
        verify(store).append(recorded);
    }

    @Test
    void record_replayReauthorizesEveryAttemptBeforeAppending() {
        SurgerySafetyPause proposal = proposal(0L);
        stubCase(surgeryCase(proposal, proposal.expectedCaseRevision()));

        service.record(proposal);
        service.record(proposal);

        verify(authority, org.mockito.Mockito.times(2)).approve(any(), eq(proposal), anyString());
        verify(store, org.mockito.Mockito.times(2)).append(any());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProofs")
    void record_invalidAuthorityProof_neverWrites(String name, SurgeryPreopAuthorityPort.Approval proof) {
        SurgerySafetyPause proposal = proposal(0L);
        stubCase(surgeryCase(proposal, proposal.expectedCaseRevision()));
        doAnswer(invocation -> {
            if ("null".equals(name)) return null;
            if ("mismatch".equals(name)) return proof;
            return approval(invocation.getArgument(2), proof.observedAt(), proof.validUntil());
        }).when(authority).approve(any(), any(), anyString());

        assertThatThrownBy(() -> service.record(proposal))
                .isInstanceOf(UpstreamUnavailableException.class);

        verify(store, never()).append(any());
    }

    @Test
    void record_authorityProofExactlyThirtySecondsOld_isAccepted() {
        SurgerySafetyPause proposal = proposal(0L);
        stubCase(surgeryCase(proposal, proposal.expectedCaseRevision()));
        doAnswer(invocation -> approval(invocation.getArgument(2), NOW.minusSeconds(30), NOW.plusSeconds(60)))
                .when(authority).approve(any(), any(), anyString());

        assertThat(service.record(proposal)).isNotNull();

        verify(store).append(any());
    }

    @Test
    void record_authorityFailure_neverLocksOrStores() {
        SurgerySafetyPause proposal = proposal(0L);
        when(cases.findById(CASE_ID)).thenReturn(Optional.of(surgeryCase(proposal, proposal.expectedCaseRevision())));
        UpstreamUnavailableException failure = new UpstreamUnavailableException("authority unavailable");
        doThrow(failure).when(authority).approve(any(), any(), anyString());

        assertThatThrownBy(() -> service.record(proposal)).isSameAs(failure);

        verify(cases, never()).lockById(any());
        verify(store, never()).append(any());
    }

    @Test
    void record_caseContextChangesAfterAuthorityLock_conflictsWithoutStoreMutation() {
        SurgerySafetyPause proposal = proposal(2L);
        SurgeryCase original = surgeryCase(proposal, proposal.expectedCaseRevision());
        SurgeryCase changed = surgeryCase(proposal, proposal.expectedCaseRevision() + 1);
        when(cases.findById(CASE_ID)).thenReturn(Optional.of(original));
        when(cases.lockById(CASE_ID)).thenReturn(Optional.of(changed));

        assertThatThrownBy(() -> service.record(proposal))
                .isInstanceOf(SurgeryRevisionConflictException.class);

        verify(authority).approve(any(), eq(proposal), anyString());
        verify(store, never()).append(any());
    }

    @Test
    void record_remoteAuthorityRunsOutsideWriteAndWriteHasNoRemoteObservation() {
        SurgerySafetyPause proposal = proposal(0L);
        stubCase(surgeryCase(proposal, proposal.expectedCaseRevision()));
        AtomicBoolean inWrite = new AtomicBoolean();
        List<String> phases = new ArrayList<>();
        doAnswer(invocation -> {
            phases.add("read");
            return run(invocation.getArgument(0));
        }).when(transactions).read(any());
        doAnswer(invocation -> {
            phases.add("outside");
            return run(invocation.getArgument(0));
        }).when(transactions).outside(any());
        doAnswer(invocation -> {
            phases.add("write");
            inWrite.set(true);
            try {
                return run(invocation.getArgument(0));
            } finally {
                inWrite.set(false);
            }
        }).when(transactions).write(any());
        doAnswer(invocation -> {
            assertThat(inWrite.get()).isFalse();
            return approval(invocation.getArgument(2), NOW, NOW.plusSeconds(60));
        }).when(authority).approve(any(), any(), anyString());
        doAnswer(invocation -> {
            assertThat(inWrite.get()).isTrue();
            return invocation.getArgument(0);
        }).when(store).append(any());

        service.record(proposal);

        assertThat(phases).containsExactly("read", "outside", "outside", "write");
    }

    @Test
    void record_freshnessExpiresAfterStore_rollsBackWriteDoubleState() {
        SurgerySafetyPause proposal = proposal(0L);
        stubCase(surgeryCase(proposal, proposal.expectedCaseRevision()));
        List<SurgerySafetyPause> persisted = new ArrayList<>();
        when(clock.now()).thenReturn(NOW, NOW, NOW.plusSeconds(31));
        doAnswer(invocation -> {
            SurgerySafetyPause value = invocation.getArgument(0);
            persisted.add(value);
            return value;
        }).when(store).append(any());
        doAnswer(invocation -> {
            List<SurgerySafetyPause> before = List.copyOf(persisted);
            try {
                return run(invocation.getArgument(0));
            } catch (RuntimeException failure) {
                persisted.clear();
                persisted.addAll(before);
                throw failure;
            }
        }).when(transactions).write(any());

        assertThatThrownBy(() -> service.record(proposal))
                .isInstanceOf(UpstreamUnavailableException.class);

        assertThat(persisted).isEmpty();
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> invalidProofs() {
        String validFingerprint = "b".repeat(64);
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("mismatch",
                        new SurgeryPreopAuthorityPort.Approval(validFingerprint, NOW, NOW.plusSeconds(60))),
                org.junit.jupiter.params.provider.Arguments.of("future",
                        new SurgeryPreopAuthorityPort.Approval(validFingerprint, NOW.plusNanos(1), NOW.plusSeconds(60))),
                org.junit.jupiter.params.provider.Arguments.of("expired",
                        new SurgeryPreopAuthorityPort.Approval(validFingerprint, NOW.minusSeconds(1), NOW)),
                org.junit.jupiter.params.provider.Arguments.of("too-old",
                        new SurgeryPreopAuthorityPort.Approval(validFingerprint, NOW.minusSeconds(31), NOW.plusSeconds(60))),
                org.junit.jupiter.params.provider.Arguments.of("too-old-by-nanosecond",
                        new SurgeryPreopAuthorityPort.Approval(validFingerprint,
                                NOW.minusSeconds(30).minusNanos(1), NOW.plusSeconds(60))),
                org.junit.jupiter.params.provider.Arguments.of("null", null));
    }

    private SurgerySafetyPause proposal(long caseRevision) {
        return SurgerySafetyPauseFixture.pause(CASE_ID, SCHEDULE_ID, SurgerySafetyPhase.BEFORE_ANAESTHESIA,
                caseRevision);
    }

    private void stubCase(SurgeryCase value) {
        when(cases.findById(value.getSurgeryCaseId())).thenReturn(Optional.of(value));
        when(cases.lockById(value.getSurgeryCaseId())).thenReturn(Optional.of(value));
    }

    private SurgeryCase surgeryCase(SurgerySafetyPause proposal, long revision) {
        SurgeryAuditActor actor = SurgeryAuditActor.human(ACCOUNT_ID, STAFF_ID);
        SurgeryCase value = SurgeryCase.create(proposal.surgeryCaseId(), REQUEST_ID,
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.fromString("80000000-0000-0000-0000-000000000001"),
                        null, UUID.fromString("90000000-0000-0000-0000-000000000001")),
                PATIENT_ID, DEPARTMENT_ID, STAFF_ID, proposal.policy().procedureCode(), "test-only safety evidence",
                SurgeryPriority.ROUTINE, NOW.minusSeconds(60), actor, CORRELATION);
        for (long current = 0; current < revision; current++) {
            value.recordBusinessMutation(actor, CORRELATION, NOW, "TEST_CONTEXT_" + current);
        }
        return value;
    }

    private static SurgeryPreopAuthorityPort.Approval approval(String fingerprint, Instant observedAt, Instant validUntil) {
        return new SurgeryPreopAuthorityPort.Approval(fingerprint, observedAt, validUntil);
    }

    private static Object run(Object supplier) {
        return ((Supplier<?>) supplier).get();
    }
}
