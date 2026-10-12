package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.dto.SurgeryCommandOutcome;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.exception.UpstreamUnavailableException;
import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
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

class AuthorizedSurgeryConsentRevocationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private final SurgeryCaseRepositoryPort cases = mock(SurgeryCaseRepositoryPort.class);
    private final SurgeryConsentRepositoryPort consents = mock(SurgeryConsentRepositoryPort.class);
    private final ManageSurgeryConsentUseCase kernel = mock(ManageSurgeryConsentUseCase.class);
    private final SurgeryConsentRevocationAuthorityPort authority = mock(SurgeryConsentRevocationAuthorityPort.class);
    private final SurgeryClockPort clock = mock(SurgeryClockPort.class);
    private final SurgeryUnitOfWorkPort transactions = mock(SurgeryUnitOfWorkPort.class);
    private final AuthorizedSurgeryConsentRevocationService service = new AuthorizedSurgeryConsentRevocationService(
            cases, consents, kernel, authority, clock, transactions);
    private SurgeryCase surgeryCase;
    private SurgeryConsentRecord consent;
    private SurgeryAuditActor actor;
    private ManageSurgeryConsentUseCase.RevokeCommand command;
    private SurgeryCommandOutcome outcome;

    @BeforeEach void prepare() {
        actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        surgeryCase = SurgeryCase.create(UUID.randomUUID(), UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TEST", "Test only",
                SurgeryPriority.ROUTINE, NOW.minusSeconds(60), actor, "test");
        consent = SurgeryConsentRecord.sign(UUID.randomUUID(), surgeryCase.getSurgeryCaseId(), SurgeryConsentType.SURGERY,
                UUID.randomUUID(), SurgeryConsentSignerType.PATIENT, UUID.randomUUID(), actor, NOW.minusSeconds(30), "signed");
        command = command(actor, consent.consentId(), "Patient withdrew consent", "key");
        outcome = new SurgeryCommandOutcome("REVOKE_CONSENT", surgeryCase.getSurgeryCaseId(), 1,
                consent.consentId(), 2, "REVOKED:SURGERY", NOW, false);
        when(cases.findById(any())).thenReturn(Optional.of(surgeryCase));
        when(cases.lockById(any())).thenReturn(Optional.of(surgeryCase));
        when(consents.findById(any())).thenReturn(Optional.of(consent));
        when(clock.now()).thenReturn(NOW);
        when(kernel.revoke(any())).thenReturn(outcome);
        doAnswer(call -> ((Supplier<?>) call.getArgument(0)).get()).when(transactions).read(any());
        doAnswer(call -> ((Supplier<?>) call.getArgument(0)).get()).when(transactions).write(any());
        doAnswer(call -> ((Supplier<?>) call.getArgument(0)).get()).when(transactions).outside(any());
        when(authority.approve(any(), any(), any(), anyString())).thenAnswer(call -> proof(call.getArgument(3)));
    }

    @Test void revoke_exactConsentAndIntent_authorizedBeforeLockedDelegation() {
        assertThat(service.revoke(command)).isSameAs(outcome);
        var order = inOrder(authority, cases, consents, kernel);
        order.verify(cases).findById(command.surgeryCaseId());
        order.verify(consents).findById(command.consentId());
        order.verify(authority).approve(eq(SurgeryPreopAuthorityPort.Context.from(surgeryCase)), eq(consent), eq(command), anyString());
        order.verify(cases).lockById(command.surgeryCaseId());
        order.verify(consents).findById(command.consentId());
        order.verify(kernel).revoke(command);
        verify(kernel, never()).sign(any());
    }

    @Test void revoke_replayAfterOriginalRevision_authorizesCurrentRevokedEvidenceEveryTime() {
        surgeryCase.recordBusinessMutation(actor, "changed", NOW, "CONSENT_REVOKED");
        consent = consent.revoke(actor, NOW, "revoked", command.reason());
        when(consents.findById(any())).thenReturn(Optional.of(consent));
        when(kernel.revoke(any())).thenReturn(outcome.asReplay());
        assertThat(service.revoke(command).replayed()).isTrue();
        assertThat(service.revoke(command).replayed()).isTrue();
        verify(authority, times(2)).approve(any(), eq(consent), eq(command), anyString());
    }

    @Test void revoke_authorityDenialOrOutage_neverLocksOrCallsKernel() {
        var failure = new UpstreamUnavailableException("source unavailable");
        doThrow(failure).when(authority).approve(any(), any(), any(), anyString());
        assertThatThrownBy(() -> service.revoke(command)).isSameAs(failure);
        verify(cases, never()).lockById(any());
        verifyNoInteractions(kernel);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "foreign"})
    void revoke_missingOrForeignConsent_deniesBeforeAuthority(String kind) {
        if (kind.equals("missing")) when(consents.findById(any())).thenReturn(Optional.empty());
        else when(consents.findById(any())).thenReturn(Optional.of(SurgeryConsentRecord.sign(consent.consentId(),
                UUID.randomUUID(), consent.consentType(), consent.signerId(), consent.signerType(),
                consent.evidenceDocumentId(), actor, consent.signedAt(), "foreign")));
        assertThatThrownBy(() -> service.revoke(command)).isInstanceOf(SurgeryRevisionConflictException.class);
        verifyNoInteractions(authority, kernel);
    }

    @ParameterizedTest @ValueSource(strings = {"null", "wrong", "future", "expired", "aged"})
    void revoke_invalidProof_deniesWithoutKernel(String kind) {
        doAnswer(call -> switch (kind) {
            case "null" -> null;
            case "wrong" -> proof("a".repeat(64));
            case "future" -> new SurgeryPreopAuthorityPort.Approval(call.getArgument(3), NOW.plusNanos(1), NOW.plusSeconds(20));
            case "expired" -> new SurgeryPreopAuthorityPort.Approval(call.getArgument(3), NOW.minusSeconds(1), NOW);
            default -> new SurgeryPreopAuthorityPort.Approval(call.getArgument(3), NOW.minusSeconds(30).minusNanos(1), NOW.plusSeconds(20));
        }).when(authority).approve(any(), any(), any(), anyString());
        assertThatThrownBy(() -> service.revoke(command)).isInstanceOf(UpstreamUnavailableException.class);
        verifyNoInteractions(kernel);
    }

    @ParameterizedTest @ValueSource(strings = {"case", "document", "audit"})
    void revoke_contextChangesDuringPreflight_conflictsWithoutKernel(String kind) {
        doAnswer(call -> {
            if (kind.equals("case")) surgeryCase.recordBusinessMutation(actor, "changed", NOW, "TEST_CHANGE");
            else {
                var changed = kind.equals("document") ? new SurgeryConsentRecord(consent.consentId(), consent.surgeryCaseId(),
                        consent.consentType(), consent.signerId(), consent.signerType(), UUID.randomUUID(), consent.auditHistory())
                        : consent.revoke(actor, NOW, "changed", "Other request");
                when(consents.findById(any())).thenReturn(Optional.of(changed));
            }
            return proof(call.getArgument(3));
        }).when(authority).approve(any(), any(), any(), anyString());
        assertThatThrownBy(() -> service.revoke(command)).isInstanceOf(SurgeryRevisionConflictException.class);
        verifyNoInteractions(kernel);
    }

    @Test void revoke_proofExpiresDuringKernel_failsAtomicWrite() {
        when(clock.now()).thenReturn(NOW, NOW.plusSeconds(20));
        assertThatThrownBy(() -> service.revoke(command)).isInstanceOf(UpstreamUnavailableException.class);
        verify(kernel).revoke(command);
    }

    @ParameterizedTest @ValueSource(strings = {"system", "no-staff"})
    void revoke_unverifiedRecorder_deniedBeforeReads(String kind) {
        var recorder = kind.equals("system") ? SurgeryAuditActor.system("surgery-service") : SurgeryAuditActor.human(actor.accountId(), null);
        assertThatThrownBy(() -> service.revoke(command(recorder, consent.consentId(), "withdrawn", "key")))
                .hasMessageContaining("Verified human staff");
        verifyNoInteractions(cases, consents, authority, kernel);
    }

    @Test void revoke_fingerprintBindsDocumentRecorderReasonKeyAndAuditNotCorrelation() {
        service.revoke(command);
        service.revoke(command(actor, consent.consentId(), "Another reason", "key"));
        service.revoke(command(actor, consent.consentId(), command.reason(), "other-key"));
        service.revoke(command(SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID()), consent.consentId(), command.reason(), "key"));
        service.revoke(new ManageSurgeryConsentUseCase.RevokeCommand(command.surgeryCaseId(), command.consentId(),
                command.expectedCaseRevision(), command.reason(), command.idempotencyKey(), actor, "new-delivery"));
        var hashes = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(authority, times(5)).approve(any(), any(), any(), hashes.capture());
        assertThat(hashes.getAllValues().subList(0, 4)).doesNotHaveDuplicates();
        assertThat(hashes.getAllValues().get(4)).isEqualTo(hashes.getAllValues().getFirst());
    }

    private SurgeryPreopAuthorityPort.Approval proof(String fingerprint) {
        return new SurgeryPreopAuthorityPort.Approval(fingerprint, NOW, NOW.plusSeconds(20));
    }
    private ManageSurgeryConsentUseCase.RevokeCommand command(SurgeryAuditActor recorder, UUID consentId, String reason, String key) {
        return new ManageSurgeryConsentUseCase.RevokeCommand(surgeryCase.getSurgeryCaseId(), consentId, 0, reason, key, recorder, "correlation");
    }
}
