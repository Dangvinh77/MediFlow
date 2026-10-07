package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryActorIdentity;
import com.mediflow.surgery.application.dto.SurgeryLifecycleCommand;
import com.mediflow.surgery.application.dto.SurgeryReadinessEvidence;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.in.CompleteSurgeryUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryChecklistRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryFinancialClearanceRepositoryPort;
import com.mediflow.surgery.application.port.out.FinancialClearanceLookupPort;
import com.mediflow.surgery.application.port.out.SurgeryLifecycleIntentPort;
import com.mediflow.surgery.application.port.out.SurgeryReadinessAuthorityPort;
import com.mediflow.surgery.application.port.out.SurgeryReadinessSnapshotPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryResultRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.exception.SurgeryRuleException;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryChecklistItem;
import com.mediflow.surgery.domain.model.SurgeryChecklistSnapshot;
import com.mediflow.surgery.domain.model.SurgeryChecklistStatus;
import com.mediflow.surgery.domain.model.SurgeryConsentRecord;
import com.mediflow.surgery.domain.model.SurgeryConsentSignerType;
import com.mediflow.surgery.domain.model.SurgeryConsentType;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryFinancialClearance;
import com.mediflow.surgery.domain.model.SurgeryPerformedItem;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SurgeryLifecycleApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-06T08:00:00Z");
    private final SurgeryCaseRepositoryPort cases = mock(SurgeryCaseRepositoryPort.class);
    private final SurgeryScheduleRepositoryPort schedules = mock(SurgeryScheduleRepositoryPort.class);
    private final SurgeryCommandReceiptPort receipts = mock(SurgeryCommandReceiptPort.class);
    private final SurgeryReadinessSnapshotPort snapshots = mock(SurgeryReadinessSnapshotPort.class);
    private final SurgeryChecklistRepositoryPort checklists = mock(SurgeryChecklistRepositoryPort.class);
    private final SurgeryConsentRepositoryPort consents = mock(SurgeryConsentRepositoryPort.class);
    private final SurgeryFinancialClearanceRepositoryPort clearances = mock(SurgeryFinancialClearanceRepositoryPort.class);
    private final FinancialClearanceLookupPort financialAuthority = mock(FinancialClearanceLookupPort.class);
    private final SurgeryResourceReservationPort resources = mock(SurgeryResourceReservationPort.class);
    private final SurgeryResultRepositoryPort results = mock(SurgeryResultRepositoryPort.class);
    private final SurgeryReadinessAuthorityPort authority = mock(SurgeryReadinessAuthorityPort.class);
    private final SurgeryLifecycleIntentPort intents = mock(SurgeryLifecycleIntentPort.class);
    private final SurgeryClockPort clock = mock(SurgeryClockPort.class);
    private final com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort events = mock(com.mediflow.surgery.application.port.out.SurgeryCareEventCapturePort.class);
    private final SurgeryReadinessEngine engine = new SurgeryReadinessEngine(Duration.ofSeconds(30),Duration.ofSeconds(5));
    private final AtomicReference<Instant> currentTime = new AtomicReference<>(NOW);
    private final UUID account = UUID.randomUUID(), staff = UUID.randomUUID(), teamPolicy = UUID.randomUUID();
    private SurgeryLifecycleApplicationService service;
    private SurgeryCase value;
    private SurgerySchedule schedule;
    private SurgeryChecklistSnapshot checklist;
    private List<SurgeryConsentRecord> consentRows;
    private SurgeryFinancialClearance grant;

    @BeforeEach void setup() {
        var actor = SurgeryAuditActor.human(account,staff);
        value = SurgeryCase.create(UUID.randomUUID(),UUID.randomUUID(),new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT,
                UUID.randomUUID(),null,UUID.randomUUID()),UUID.randomUUID(),UUID.randomUUID(),staff,"TEST","Test-only",
                SurgeryPriority.ROUTINE,NOW.minusSeconds(60),actor,"local-lifecycle");
        value.beginPreop(actor,"local-lifecycle",NOW.minusSeconds(50));
        schedule = new SurgerySchedule(UUID.randomUUID(),value.getSurgeryCaseId(),1,UUID.randomUUID(),NOW.plusSeconds(60),NOW.plusSeconds(600),
                List.of(new SurgeryTeamAssignment(staff,SurgeryTeamRole.PRIMARY_SURGEON)));
        checklist = new SurgeryChecklistSnapshot(UUID.randomUUID(),value.getSurgeryCaseId(),UUID.randomUUID(),1,1,List.of(
                new SurgeryChecklistItem(UUID.randomUUID(),value.getSurgeryCaseId(),UUID.randomUUID(),"TEST-ONLY",true,1,
                        SurgeryChecklistStatus.SATISFIED,UUID.randomUUID(),1L,1)));
        consentRows = Arrays.stream(SurgeryConsentType.values()).map(type -> SurgeryConsentRecord.sign(UUID.randomUUID(),value.getSurgeryCaseId(),type,
                value.getPatientId(),SurgeryConsentSignerType.PATIENT,UUID.randomUUID(),actor,NOW.minusSeconds(40),"local-lifecycle")).toList();
        grant = new SurgeryFinancialClearance(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),value.getPatientId(),value.getSurgeryCaseId(),
                value.getCareEpisode().type(),value.getCareEpisode().episodeId(),null,BigDecimal.ONE,"VND","CASH",NOW.minusSeconds(30),NOW.plusSeconds(60),"a".repeat(64));
        when(cases.findById(value.getSurgeryCaseId())).thenAnswer(ignored -> Optional.of(value));
        when(cases.lockById(value.getSurgeryCaseId())).thenAnswer(ignored -> Optional.of(value));
        when(schedules.findByCaseId(value.getSurgeryCaseId())).thenReturn(Optional.of(schedule));
        when(checklists.findSnapshotByCaseId(value.getSurgeryCaseId())).thenReturn(Optional.of(checklist));
        when(consents.findByCaseId(value.getSurgeryCaseId())).thenAnswer(ignored -> consentRows);
        when(clearances.lockById(grant.clearanceId())).thenAnswer(ignored -> Optional.of(grant));
        when(clearances.findById(grant.clearanceId())).thenAnswer(ignored -> Optional.of(grant));
        when(financialAuthority.observe(any(),anyString())).thenAnswer(ignored ->
                new FinancialClearanceLookupPort.Observation(true,currentTime.get(),null));
        when(clock.now()).thenAnswer(ignored -> currentTime.get());
        when(receipts.claim(any(),anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(SurgeryCommandReceiptPort.State.NEW,UUID.randomUUID(),null,null));
        when(authority.observe(any(),any(),anyString())).thenAnswer(ignored -> evidence());
        service = new SurgeryLifecycleApplicationService(cases,schedules,receipts,snapshots,checklists,consents,clearances,resources,results,authority,intents,clock,engine,financialAuthority,events);
    }

    @Test void evaluate_validLocalAndAuthorityProofs_storesReadyAndHeldIntentNotBrokerOutput() {
        var outcome = service.evaluate(command());
        assertThat(outcome.state()).isEqualTo("READY");
        assertThat(value.getStatus()).isEqualTo(SurgeryStatus.READY);
        assertThat(value.getReadinessSnapshot().validUntil()).isEqualTo(grant.expiresAt());
        verify(intents).holdReady(value,schedule,value.getReadinessSnapshot(),"local-lifecycle");
        verify(resources,never()).reserve(any(),any());
        var order = inOrder(authority,financialAuthority,cases,resources,clock);
        order.verify(authority).observe(any(),any(),anyString()); order.verify(financialAuthority).observe(grant,"local-lifecycle");
        order.verify(cases).lockById(value.getSurgeryCaseId());
        order.verify(resources).lockForMutation(schedule); order.verify(authority).reconcile(any(),any(),any()); order.verify(clock).now();
    }
    @ParameterizedTest @ValueSource(strings = {"missing-finance","wrong-patient","expired-finance","revoked-anesthesia","stale-checklist","unknown"})
    void evaluate_eachInvalidOwnedOrExternalProof_deniesWithoutReadyOrIntent(String kind) {
        if (kind.equals("missing-finance")) when(clearances.lockById(grant.clearanceId())).thenReturn(Optional.empty());
        if (kind.equals("wrong-patient")) grant = new SurgeryFinancialClearance(grant.clearanceId(),grant.invoiceId(),grant.accountId(),UUID.randomUUID(),
                grant.surgeryCaseId(),grant.episodeType(),grant.episodeId(),null,grant.amount(),grant.currency(),grant.paymentMethod(),grant.grantedAt(),grant.expiresAt(),grant.fingerprint());
        if (kind.equals("expired-finance")) currentTime.set(grant.expiresAt());
        if (kind.equals("revoked-anesthesia")) consentRows = consentRows.stream().map(item -> item.consentType() != SurgeryConsentType.ANESTHESIA ? item
                : item.revoke(SurgeryAuditActor.human(account,staff),NOW.minusSeconds(1),"local-lifecycle","Test revoke")).toList();
        if (kind.equals("stale-checklist")) when(checklists.findSnapshotByCaseId(value.getSurgeryCaseId())).thenReturn(Optional.empty());
        if (kind.equals("unknown")) when(authority.observe(any(),any(),anyString())).thenAnswer(ignored -> {
            var original = evidence();
            return new SurgeryReadinessEvidence(original.surgeryCaseId(),original.patientId(),original.departmentId(),original.episode(),original.caseRevision(),
                    original.scheduleId(),1,original.proofs().stream().filter(proof -> proof.type() != SurgeryDependencyType.INDICATION).toList());
        });
        assertThat(service.evaluate(command()).state()).isEqualTo("NOT_READY");
        assertThat(value.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        verifyNoInteractions(intents);
        verify(cases,never()).save(any(),org.mockito.ArgumentMatchers.anyLong());
    }
    @Test void evaluate_billingUnavailable_neverMutatesOrAcquiresResourceLocks() {
        when(financialAuthority.observe(any(),anyString())).thenThrow(new com.mediflow.surgery.application.exception.UpstreamUnavailableException("test failure"));
        assertThatThrownBy(()->service.evaluate(command())).isInstanceOf(com.mediflow.surgery.application.exception.UpstreamUnavailableException.class);
        verify(cases,never()).lockById(any()); verifyNoInteractions(resources,intents,snapshots);
        assertThat(value.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
    }
    @Test void evaluate_financialObservationBecomesStaleDuringResourceWait_deniesWithoutReady() {
        when(authority.observe(any(),any(),anyString())).thenAnswer(call -> {
            var original=evidence();
            var proofs=original.proofs().stream().map(proof -> proof.type()==SurgeryDependencyType.FINANCIAL_CLEARANCE ? proof :
                    new SurgeryReadinessEvidence.Proof(proof.type(),proof.sourceId(),proof.revision(),proof.decision(),
                            NOW.plusSeconds(1),proof.validFrom(),proof.validUntil())).toList();
            return new SurgeryReadinessEvidence(original.surgeryCaseId(),original.patientId(),original.departmentId(),original.episode(),
                    original.caseRevision(),original.scheduleId(),original.scheduleRevision(),proofs);
        });
        doAnswer(call -> { currentTime.set(NOW.plusSeconds(31)); return null; }).when(resources).lockForMutation(schedule);
        assertThat(service.evaluate(command()).state()).isEqualTo("NOT_READY");
        var stored=ArgumentCaptor.forClass(com.mediflow.surgery.domain.model.ReadinessSnapshot.class);
        verify(snapshots).store(stored.capture());
        assertThat(stored.getValue().financialClearanceValid()).isFalse();
        assertThat(stored.getValue().indicationValid()).isTrue();
        assertThat(stored.getValue().mandatoryChecklistComplete()).isTrue();
        assertThat(stored.getValue().teamEligible()).isTrue();
        assertThat(stored.getValue().scheduleConfirmed()).isTrue();
        verifyNoInteractions(intents);
        assertThat(value.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
    }
    @ParameterizedTest @ValueSource(strings={"evaluate","finalize","start"})
    void decision_billingRevokedOrNetRefundedGrant_deniesEvenIfLocalImmutableGrantStillValid(String operation) {
        if (!operation.equals("evaluate")) ready();
        if (operation.equals("start")) value.finalizeSchedule(SurgeryAuditActor.human(account,staff),"local-lifecycle",NOW);
        when(financialAuthority.observe(any(),anyString())).thenReturn(new FinancialClearanceLookupPort.Observation(false,NOW,NOW.plusSeconds(30)));
        var result=switch(operation) { case "evaluate" -> service.evaluate(command()); case "finalize" -> service.finalizeSchedule(command()); default -> service.start(command()); };
        assertThat(result.state()).isEqualTo("NOT_READY");
        assertThat(value.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        verify(resources,never()).reserve(any(),any());
        verify(resources,never()).markInUse(any(),any(),org.mockito.ArgumentMatchers.anyLong(),any());
        if (!operation.equals("evaluate")) verify(cases).save(value,command().expectedCaseRevision()-1);
    }
    @Test void finalize_validReady_reservesBeforePersistedScheduledTransition() {
        ready();
        var outcome = service.finalizeSchedule(command());
        assertThat(outcome.state()).isEqualTo("SCHEDULED");
        var order = inOrder(resources,cases,receipts);
        order.verify(resources).reserve(schedule,NOW); order.verify(cases).save(value,2); order.verify(receipts).complete(any(),any(),anyString(),any(),any());
    }
    @Test void start_scheduled_rechecksAndMarksOnlyExactBookedRevisionInUse() {
        scheduled();
        var outcome = service.start(command());
        assertThat(outcome.state()).isEqualTo("IN_PROGRESS");
        assertThat(value.getStartedAt()).isEqualTo(NOW);
        verify(resources).markInUse(value.getSurgeryCaseId(),schedule.scheduleId(),1,NOW);
    }
    @Test void start_fromReady_rejectsBeforeAnyResourceMutation() {
        ready();
        assertThatThrownBy(() -> service.start(command())).isInstanceOf(SurgeryRuleException.class);
        verifyNoInteractions(resources);
    }
    @Test void start_revokedConsent_commitsDenialWithInvalidationAndExactReleaseNotThrowRollback() {
        scheduled();
        consentRows = List.of(consentRows.getFirst());
        assertThat(service.start(command()).state()).isEqualTo("NOT_READY");
        assertThat(value.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        verify(resources).release(value.getSurgeryCaseId(),schedule.scheduleId(),1,NOW);
        verify(resources,never()).markInUse(any(),any(),org.mockito.ArgumentMatchers.anyLong(),any());
        assertThat(value.getReadinessSnapshot()).isNull();
    }
    @Test void finalize_resourceLockWaitExpiresDecision_commitsPreopAndNeverReserves() {
        ready();
        doAnswer(ignored -> { currentTime.set(NOW.plusSeconds(61)); return null; }).when(resources).lockForMutation(schedule);
        assertThat(service.finalizeSchedule(command()).state()).isEqualTo("READINESS_EXPIRED");
        assertThat(value.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        verify(resources,never()).reserve(any(),any());
    }
    @Test void evaluate_replayStillAuthorizesAndRetainsOriginalTimestampWithoutRepeatingAuthorityLookups() {
        var bytes = new AtomicReference<byte[]>();
        doAnswer(call -> { bytes.set(call.getArgument(3)); return null; }).when(receipts).complete(any(),any(),anyString(),any(),any());
        var original = command();
        var outcome = service.evaluate(original);
        when(receipts.claim(any(),anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(SurgeryCommandReceiptPort.State.REPLAY,
                UUID.randomUUID(),"EVALUATE_READINESS",bytes.get()));
        currentTime.set(NOW.plusSeconds(20));
        var replay = service.evaluate(original);
        assertThat(replay.replayed()).isTrue(); assertThat(replay.occurredAt()).isEqualTo(outcome.occurredAt());
        verify(authority,times(2)).authorize(original.actor(),"EVALUATE_READINESS",value.getSurgeryCaseId());
        verify(authority,times(1)).observe(any(),any(),anyString());
        verify(intents,times(1)).holdReady(any(),any(),any(),anyString());
    }
    @Test void evaluate_authorizationDenied_doesNotClaimReceiptOrTouchCase() {
        doThrow(new SurgeryRuleException("SURGERY_FORBIDDEN","Denied")).when(authority).authorize(any(),anyString(),any());
        assertThatThrownBy(() -> service.evaluate(command())).isInstanceOf(SurgeryRuleException.class);
        verifyNoInteractions(receipts,cases,resources,intents);
    }
    @Test void evaluate_caseChangesDuringLookup_conflictsBeforeResourceLock() {
        doAnswer(ignored -> { value.recordBusinessMutation(SurgeryAuditActor.human(account,staff),"local-lifecycle",NOW,"TEST_CHANGE"); return evidence(); })
                .when(authority).observe(any(),any(),anyString());
        assertThatThrownBy(() -> service.evaluate(command())).isInstanceOf(SurgeryRevisionConflictException.class);
        verifyNoInteractions(resources,intents);
    }
    @Test void complete_inProgress_persistsResultAndExactReleaseAndHeldIntent() {
        started();
        var outcome = service.complete(completion());
        assertThat(outcome.state()).isEqualTo("COMPLETED");
        verify(results).create(any()); verify(resources).release(value.getSurgeryCaseId(),schedule.scheduleId(),1,NOW);
        verify(intents).holdCompleted(any(),any(),any(),anyString());
    }
    @Test void complete_existingResult_neverReplacesItOrReleasesResources() {
        started();
        when(results.findByCaseId(value.getSurgeryCaseId())).thenReturn(Optional.of(mock(com.mediflow.surgery.domain.model.SurgeryResult.class)));
        assertThatThrownBy(() -> service.complete(completion())).isInstanceOf(SurgeryRevisionConflictException.class);
        verify(resources,never()).release(any(),any(),org.mockito.ArgumentMatchers.anyLong(),any());
        verifyNoInteractions(intents);
    }
    private void ready() {
        var proof = evidence();
        proof = new SurgeryReadinessEvidence(proof.surgeryCaseId(),proof.patientId(),proof.departmentId(),proof.episode(),proof.caseRevision(),proof.scheduleId(),1,
                proof.proofs().stream().map(item -> item.type() != SurgeryDependencyType.FINANCIAL_CLEARANCE ? item : new SurgeryReadinessEvidence.Proof(
                        item.type(),item.sourceId(),item.revision(),item.decision(),item.observedAt(),item.validFrom(),grant.expiresAt())).toList());
        value.markReady(engine.evaluate(value,schedule,proof,NOW.minusSeconds(1)),SurgeryAuditActor.human(account,staff),"local-lifecycle");
    }
    private void scheduled() { ready(); value.finalizeSchedule(SurgeryAuditActor.human(account,staff),"local-lifecycle",NOW.minusSeconds(1)); }
    private void started() { scheduled(); value.start(engine.evaluate(value,schedule,evidence(),NOW.minusSeconds(1)),SurgeryAuditActor.human(account,staff),"local-lifecycle",NOW.minusSeconds(1)); }
    private SurgeryLifecycleCommand command() { return new SurgeryLifecycleCommand(value.getSurgeryCaseId(),value.getRevision(),1,"test-key",new SurgeryActorIdentity(account,staff),"local-lifecycle"); }
    private CompleteSurgeryUseCase.Command completion() {
        return new CompleteSurgeryUseCase.Command(command(),"TEST","TEST-METHOD","TEST-OUTCOME",null,NOW.minusSeconds(1),NOW,
                List.of(new SurgeryPerformedItem(UUID.randomUUID(),"TEST-ITEM","TEST-PRICE",BigDecimal.ONE)));
    }
    private SurgeryReadinessEvidence evidence() {
        var proofs = Arrays.stream(SurgeryDependencyType.values()).map(type -> {
            UUID id = switch (type) {
                case INDICATION -> value.getSurgeryRequestId(); case CHECKLIST -> checklist.checklistSnapshotId();
                case SURGERY_CONSENT -> consentRows.stream().filter(item -> item.consentType() == SurgeryConsentType.SURGERY).findFirst().map(SurgeryConsentRecord::consentId).orElse(UUID.randomUUID());
                case ANESTHESIA_CONSENT -> consentRows.stream().filter(item -> item.consentType() == SurgeryConsentType.ANESTHESIA).findFirst().map(SurgeryConsentRecord::consentId).orElse(UUID.randomUUID());
                case TEAM_ELIGIBILITY -> teamPolicy; case SCHEDULE -> schedule.scheduleId(); case FINANCIAL_CLEARANCE -> grant.clearanceId();
            };
            long revision = type == SurgeryDependencyType.SCHEDULE || type == SurgeryDependencyType.CHECKLIST ? 1 : 0;
            return new SurgeryReadinessEvidence.Proof(type,id,revision,SurgeryReadinessEvidence.Decision.SATISFIED,NOW.minusSeconds(1),NOW.minusSeconds(60),NOW.plusSeconds(100));
        }).toList();
        return new SurgeryReadinessEvidence(value.getSurgeryCaseId(),value.getPatientId(),value.getDepartmentId(),value.getCareEpisode(),
                value.getRevision(),schedule.scheduleId(),schedule.revision(),proofs);
    }
}
