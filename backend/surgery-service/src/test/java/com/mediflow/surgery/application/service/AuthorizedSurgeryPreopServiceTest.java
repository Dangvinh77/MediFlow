package com.mediflow.surgery.application.service;

import com.mediflow.common.exception.ResourceNotFoundException;
import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.in.*;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.domain.model.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthorizedSurgeryPreopServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private final SurgeryCaseRepositoryPort cases = mock(SurgeryCaseRepositoryPort.class);
    private final UpdateChecklistItemUseCase checklist = mock(UpdateChecklistItemUseCase.class);
    private final ManageSurgeryConsentUseCase consents = mock(ManageSurgeryConsentUseCase.class);
    private final SurgeryPreopAuthorityPort authority = mock(SurgeryPreopAuthorityPort.class);
    private final SurgeryClockPort clock = mock(SurgeryClockPort.class);
    private final SurgeryUnitOfWorkPort transactions = mock(SurgeryUnitOfWorkPort.class);
    private final AuthorizedSurgeryPreopService service = new AuthorizedSurgeryPreopService(
            cases, checklist, consents, authority, clock, transactions);
    private SurgeryCase surgeryCase;
    private SurgeryAuditActor actor;
    private SurgeryCommandOutcome outcome;

    @BeforeEach void prepare() {
        actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        surgeryCase = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TEST", "Test only",
                SurgeryPriority.ROUTINE, NOW.minusSeconds(1), actor, "test-correlation");
        outcome = new SurgeryCommandOutcome("TEST", surgeryCase.getSurgeryCaseId(), 1,
                UUID.randomUUID(), 1, "TEST", NOW, false);
        when(cases.findById(any())).thenReturn(Optional.of(surgeryCase));
        when(cases.lockById(any())).thenReturn(Optional.of(surgeryCase));
        when(clock.now()).thenReturn(NOW);
        doAnswer(call -> ((Supplier<?>) call.getArgument(0)).get()).when(transactions).read(any());
        doAnswer(call -> ((Supplier<?>) call.getArgument(0)).get()).when(transactions).write(any());
        doAnswer(call -> ((Supplier<?>) call.getArgument(0)).get()).when(transactions).outside(any());
        when(checklist.update(any())).thenReturn(outcome);
        when(consents.sign(any())).thenReturn(outcome);
        when(authority.approveChecklist(any(), any(), anyString())).thenAnswer(call -> approval(call.getArgument(2)));
        when(authority.approveConsent(any(), any(), anyString())).thenAnswer(call -> approval(call.getArgument(2)));
    }

    @Test void checklist_validAuthority_isObservedOutsideWriteThenCheckedAfterLock() {
        var command = checklistCommand(actor);
        assertThat(service.updateChecklist(command)).isSameAs(outcome);
        var order = inOrder(cases, authority, clock, checklist);
        order.verify(cases).findById(command.surgeryCaseId());
        order.verify(authority).approveChecklist(eq(SurgeryPreopAuthorityPort.Context.from(surgeryCase)), eq(command), anyString());
        order.verify(cases).lockById(command.surgeryCaseId());
        order.verify(clock).now();
        order.verify(checklist).update(command);
        verify(transactions, times(2)).outside(any());
        verifyNoInteractions(consents);
    }

    @Test void consent_verifiedSignerInput_isCheckedIndependentlyOfRecorder() {
        var command = consentCommand(actor);
        assertThat(service.recordConsent(command)).isSameAs(outcome);
        assertThat(command.signerId()).isNotEqualTo(actor.verifiedStaffId());
        verify(authority).approveConsent(eq(SurgeryPreopAuthorityPort.Context.from(surgeryCase)), eq(command), anyString());
        verify(consents).sign(command); verifyNoInteractions(checklist);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void everyAttempt_evenReceiptReplay_reauthorizesBeforeDelegation(boolean consent) {
        if (consent) when(consents.sign(any())).thenReturn(outcome.asReplay());
        else when(checklist.update(any())).thenReturn(outcome.asReplay());
        var sameChecklist = checklistCommand(actor);
        var sameConsent = consentCommand(actor);
        for (int i = 0; i < 2; i++) assertThat((consent ? service.recordConsent(sameConsent)
                : service.updateChecklist(sameChecklist)).replayed()).isTrue();
        if (consent) verify(authority, times(2)).approveConsent(any(), any(), anyString());
        else verify(authority, times(2)).approveChecklist(any(), any(), anyString());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void sourceOutage_neverLocksOrMutates(boolean consent) {
        var failure = new UpstreamUnavailableException("test-only source outage");
        if (consent) doThrow(failure).when(authority).approveConsent(any(), any(), anyString());
        else doThrow(failure).when(authority).approveChecklist(any(), any(), anyString());
        assertThatThrownBy(() -> run(consent)).isSameAs(failure);
        verify(cases, never()).lockById(any()); verifyNoInteractions(checklist, consents);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void missingOrWrongFingerprintProof_neverMutates(boolean consent) {
        var wrong = approval("a".repeat(64));
        if (consent) doReturn(wrong, (Object) null).when(authority).approveConsent(any(), any(), anyString());
        else doReturn(wrong, (Object) null).when(authority).approveChecklist(any(), any(), anyString());
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> run(consent)).isInstanceOf(UpstreamUnavailableException.class);
        verifyNoInteractions(checklist, consents);
    }

    @ParameterizedTest @ValueSource(longs = {30, 31})
    void observationAge_boundaryIsInclusiveButNotPastThirtySeconds(long age) {
        doAnswer(call -> new SurgeryPreopAuthorityPort.Approval(call.getArgument(2), NOW.minusSeconds(age), NOW.plusSeconds(5)))
                .when(authority).approveChecklist(any(), any(), anyString());
        if (age == 30) assertThat(service.updateChecklist(checklistCommand(actor))).isSameAs(outcome);
        else {
            assertThatThrownBy(() -> service.updateChecklist(checklistCommand(actor))).isInstanceOf(UpstreamUnavailableException.class);
            verifyNoInteractions(checklist, consents);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void exactExpiryAfterLock_deniesBothMutations(boolean consent) {
        when(cases.lockById(any())).thenAnswer(call -> {
            when(clock.now()).thenReturn(NOW.plusSeconds(20)); return Optional.of(surgeryCase);
        });
        assertThatThrownBy(() -> run(consent)).isInstanceOf(UpstreamUnavailableException.class);
        verifyNoInteractions(checklist, consents);
    }

    @Test void futureObservation_isNotTrusted() {
        doAnswer(call -> new SurgeryPreopAuthorityPort.Approval(call.getArgument(2), NOW.plusNanos(1), NOW.plusSeconds(10)))
                .when(authority).approveConsent(any(), any(), anyString());
        assertThatThrownBy(() -> service.recordConsent(consentCommand(actor))).isInstanceOf(UpstreamUnavailableException.class);
        verifyNoInteractions(checklist, consents);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void authorityAgesDuringKernel_preventsCommit(boolean consent) {
        when(clock.now()).thenReturn(NOW, NOW.plusSeconds(20));
        assertThatThrownBy(() -> run(consent)).isInstanceOf(UpstreamUnavailableException.class);
        if (consent) verify(consents).sign(any()); else verify(checklist).update(any());
    }

    @Test void caseChangedAfterPreflight_conflictsWithoutDelegating() {
        when(cases.lockById(any())).thenAnswer(call -> {
            surgeryCase.recordBusinessMutation(actor, "other-command", NOW, "TEST_CHANGE");
            return Optional.of(surgeryCase);
        });
        assertThatThrownBy(() -> service.updateChecklist(checklistCommand(actor))).isInstanceOf(SurgeryRevisionConflictException.class);
        verifyNoInteractions(checklist, consents);
    }

    @Test void missingCase_isTypedOpaqueNotFound() {
        when(cases.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordConsent(consentCommand(actor))).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(authority, checklist, consents);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void confirmedAuthorityDenial_isNotConvertedIntoOutageOrSuccessfulReplay(boolean consent) {
        var denial = new com.mediflow.surgery.domain.exception.SurgeryRuleException(
                "SURGERY_PREOP_AUTHORITY_DENIED", "Test-only denied relationship");
        if (consent) doThrow(denial).when(authority).approveConsent(any(), any(), anyString());
        else doThrow(denial).when(authority).approveChecklist(any(), any(), anyString());
        assertThatThrownBy(() -> run(consent)).isSameAs(denial);
        verify(cases, never()).lockById(any()); verifyNoInteractions(checklist, consents);
    }

    @Test void changedPatientEvenAtSameRevision_conflictsWithoutEffects() {
        var changed = SurgeryCase.create(surgeryCase.getSurgeryCaseId(), surgeryCase.getSurgeryRequestId(),
                surgeryCase.getCareEpisode(), UUID.randomUUID(), surgeryCase.getDepartmentId(),
                surgeryCase.getRequestedBy(), "TEST", "Test only", SurgeryPriority.ROUTINE,
                NOW.minusSeconds(1), actor, "test-correlation");
        when(cases.lockById(any())).thenReturn(Optional.of(changed));
        assertThatThrownBy(() -> service.updateChecklist(checklistCommand(actor))).isInstanceOf(SurgeryRevisionConflictException.class);
        verifyNoInteractions(checklist, consents);
    }

    @Test void caseDisappearedAfterProof_isConflict() {
        when(cases.lockById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recordConsent(consentCommand(actor))).isInstanceOf(SurgeryRevisionConflictException.class);
        verifyNoInteractions(checklist, consents);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void systemOrUnsignedRecorder_neverReachesSourceOrStorage(boolean consent) {
        for (var untrusted : new SurgeryAuditActor[]{SurgeryAuditActor.system("test"), SurgeryAuditActor.human(actor.accountId(), null)}) {
            assertThatThrownBy(() -> { if (consent) service.recordConsent(consentCommand(untrusted));
                else service.updateChecklist(checklistCommand(untrusted)); }).isInstanceOf(com.mediflow.surgery.domain.exception.SurgeryRuleException.class);
        }
        verifyNoInteractions(cases, authority, checklist, consents);
    }

    @Test void differentEvidence_andDifferentActor_haveDifferentProofFingerprints() {
        var original = checklistCommand(actor);
        service.updateChecklist(original);
        service.updateChecklist(new UpdateChecklistItemUseCase.Command(original.surgeryCaseId(), original.checklistItemId(),
                0, 1, 0, SurgeryChecklistStatus.SATISFIED, UUID.randomUUID(), 2L, "key", actor, "other-correlation"));
        service.updateChecklist(new UpdateChecklistItemUseCase.Command(original.surgeryCaseId(), original.checklistItemId(),
                original.expectedCaseRevision(), original.expectedSnapshotRevision(), original.expectedItemRevision(),
                original.status(), original.evidenceReferenceId(), original.evidenceRevision(), original.idempotencyKey(),
                SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID()), original.correlationId()));
        var capture = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(authority, times(3)).approveChecklist(any(), any(), capture.capture());
        assertThat(capture.getAllValues()).doesNotHaveDuplicates().allMatch(value -> value.matches("[a-f0-9]{64}"));
    }

    @Test void proof_invalidHashOrInterval_isRejectedAtConstruction() {
        assertThatThrownBy(() -> approval("invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SurgeryPreopAuthorityPort.Approval("b".repeat(64), NOW, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    private SurgeryCommandOutcome run(boolean consent) {
        return consent ? service.recordConsent(consentCommand(actor)) : service.updateChecklist(checklistCommand(actor));
    }
    private SurgeryPreopAuthorityPort.Approval approval(String fingerprint) {
        return new SurgeryPreopAuthorityPort.Approval(fingerprint, NOW, NOW.plusSeconds(20));
    }
    private UpdateChecklistItemUseCase.Command checklistCommand(SurgeryAuditActor recorder) {
        return new UpdateChecklistItemUseCase.Command(surgeryCase.getSurgeryCaseId(), UUID.randomUUID(),
                0, 1, 0, SurgeryChecklistStatus.SATISFIED, UUID.randomUUID(), 1L, "key", recorder, "correlation");
    }
    private ManageSurgeryConsentUseCase.SignCommand consentCommand(SurgeryAuditActor recorder) {
        return new ManageSurgeryConsentUseCase.SignCommand(surgeryCase.getSurgeryCaseId(), 0,
                SurgeryConsentType.SURGERY, UUID.randomUUID(), SurgeryConsentSignerType.PATIENT,
                UUID.randomUUID(), "key", recorder, "correlation");
    }
}
