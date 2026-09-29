package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.ManageSurgeryConsentUseCase;
import com.mediflow.surgery.application.exception.SurgeryRevisionConflictException;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.application.port.out.SurgeryConsentRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryResourceReservationPort;
import com.mediflow.surgery.application.port.out.SurgeryScheduleRepositoryPort;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.ReadinessSnapshot;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryConsentAction;
import com.mediflow.surgery.domain.model.SurgeryConsentRecord;
import com.mediflow.surgery.domain.model.SurgeryConsentSignerType;
import com.mediflow.surgery.domain.model.SurgeryConsentType;
import com.mediflow.surgery.domain.model.SurgeryDependencyRevision;
import com.mediflow.surgery.domain.model.SurgeryDependencyType;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgerySchedule;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import com.mediflow.surgery.domain.model.SurgeryTeamAssignment;
import com.mediflow.surgery.domain.model.SurgeryTeamRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurgeryConsentApplicationServiceTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-29T01:00:00Z");
    private static final Instant NOW = REQUESTED_AT.plusSeconds(30);
    private static final String CORRELATION_ID = "consent-unit-test";

    @Mock private SurgeryCaseRepositoryPort cases;
    @Mock private SurgeryConsentRepositoryPort consents;
    @Mock private SurgeryScheduleRepositoryPort schedules;
    @Mock private SurgeryResourceReservationPort reservations;
    @Mock private SurgeryCommandReceiptPort receipts;
    @Mock private SurgeryClockPort clock;
    @InjectMocks private SurgeryConsentApplicationService service;

    @Test
    void signConsent_newTypedConsent_storesSeparateSignerAndRecorderAudit() {
        UUID caseId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        UUID signerId = UUID.randomUUID();
        SurgeryAuditActor recorder = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = newPreopCase(caseId, recorder);
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.NEW, receiptId, null, null));
        when(cases.lockById(caseId)).thenReturn(Optional.of(surgeryCase));
        when(consents.findByCaseId(caseId)).thenReturn(List.of());
        when(clock.now()).thenReturn(NOW);

        var outcome = service.sign(new ManageSurgeryConsentUseCase.SignCommand(
                caseId, 1, SurgeryConsentType.ANESTHESIA, signerId,
                SurgeryConsentSignerType.PATIENT, UUID.randomUUID(), "sign-1", recorder, CORRELATION_ID));

        assertThat(outcome.state()).isEqualTo("ACTIVE:ANESTHESIA");
        assertThat(outcome.caseRevision()).isEqualTo(2);
        assertThat(surgeryCase.getRevision()).isEqualTo(2);
        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        verify(cases).save(surgeryCase, 1);

        ArgumentCaptor<SurgeryConsentRecord> saved = ArgumentCaptor.forClass(SurgeryConsentRecord.class);
        verify(consents).save(saved.capture());
        SurgeryConsentRecord consent = saved.getValue();
        assertThat(consent.consentType()).isEqualTo(SurgeryConsentType.ANESTHESIA);
        assertThat(consent.signerId()).isEqualTo(signerId);
        assertThat(consent.signedAt()).isEqualTo(NOW);
        assertThat(consent.auditHistory()).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(SurgeryConsentAction.SIGNED);
            assertThat(entry.recordedBy()).isEqualTo(recorder);
        });
        verify(receipts).complete(eq(receiptId), eq(caseId), eq("SIGN_CONSENT"), any(), eq(NOW));
        verifyNoInteractions(schedules, reservations);
    }

    @Test
    void signConsent_systemRecorder_rejectsBeforeReceiptClaim() {
        ManageSurgeryConsentUseCase.SignCommand command = new ManageSurgeryConsentUseCase.SignCommand(
                UUID.randomUUID(), 0, SurgeryConsentType.SURGERY, UUID.randomUUID(),
                SurgeryConsentSignerType.PATIENT, null, "sign-system",
                SurgeryAuditActor.system("clinical-service"), CORRELATION_ID);

        assertThatThrownBy(() -> service.sign(command))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cases, consents, schedules, reservations, receipts, clock);
    }

    @Test
    void signConsent_duplicateActiveType_rejectsWithoutAppendingAnotherConsent() {
        UUID caseId = UUID.randomUUID();
        SurgeryAuditActor recorder = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = newPreopCase(caseId, recorder);
        SurgeryConsentRecord active = SurgeryConsentRecord.sign(UUID.randomUUID(), caseId,
                SurgeryConsentType.SURGERY, UUID.randomUUID(), SurgeryConsentSignerType.PATIENT,
                null, recorder, REQUESTED_AT.plusSeconds(2), CORRELATION_ID);
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.NEW, UUID.randomUUID(), null, null));
        when(cases.lockById(caseId)).thenReturn(Optional.of(surgeryCase));
        when(consents.findByCaseId(caseId)).thenReturn(List.of(active));

        assertThatThrownBy(() -> service.sign(new ManageSurgeryConsentUseCase.SignCommand(
                caseId, 1, SurgeryConsentType.SURGERY, UUID.randomUUID(),
                SurgeryConsentSignerType.PATIENT, null, "sign-duplicate", recorder, CORRELATION_ID)))
                .isInstanceOf(SurgeryRevisionConflictException.class);

        verify(consents, never()).save(any());
        verify(cases, never()).save(any(), anyLong());
        verify(receipts, never()).complete(any(), any(), anyString(), any(), any());
        verifyNoInteractions(clock, schedules, reservations);
    }

    @Test
    void revokeConsent_wrongCaseConsent_rejectsWithoutSavingAudit() {
        UUID caseId = UUID.randomUUID();
        SurgeryAuditActor recorder = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = newPreopCase(caseId, recorder);
        SurgeryConsentRecord foreignConsent = SurgeryConsentRecord.sign(UUID.randomUUID(),
                UUID.randomUUID(), SurgeryConsentType.SURGERY, UUID.randomUUID(),
                SurgeryConsentSignerType.PATIENT, null, recorder, REQUESTED_AT.plusSeconds(2),
                CORRELATION_ID);
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.NEW, UUID.randomUUID(), null, null));
        when(cases.lockById(caseId)).thenReturn(Optional.of(surgeryCase));
        when(consents.findById(foreignConsent.consentId())).thenReturn(Optional.of(foreignConsent));

        assertThatThrownBy(() -> service.revoke(new ManageSurgeryConsentUseCase.RevokeCommand(
                caseId, foreignConsent.consentId(), 1, "wrong case", "revoke-1", recorder, CORRELATION_ID)))
                .isInstanceOf(SurgeryRevisionConflictException.class);

        verify(consents).findById(foreignConsent.consentId());
        verifyNoInteractions(clock, schedules, reservations);
    }

    @Test
    void revokeConsent_afterStart_rejectsBeforeLoadingConsentOrReleasingResources() {
        UUID caseId = UUID.randomUUID();
        SurgeryAuditActor recorder = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = inProgressCase(caseId, recorder);
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.NEW, UUID.randomUUID(), null, null));
        when(cases.lockById(caseId)).thenReturn(Optional.of(surgeryCase));

        assertThatThrownBy(() -> service.revoke(new ManageSurgeryConsentUseCase.RevokeCommand(
                caseId, UUID.randomUUID(), surgeryCase.getRevision(), "withdrawn", "revoke-after-start",
                recorder, CORRELATION_ID)))
                .isInstanceOf(SurgeryRevisionConflictException.class);

        verify(consents, never()).findById(any());
        verify(consents, never()).save(any());
        verify(cases, never()).save(any(), anyLong());
        verify(receipts, never()).complete(any(), any(), anyString(), any(), any());
        verifyNoInteractions(clock, schedules, reservations);
    }

    @Test
    void signConsent_whileScheduled_invalidatesReadinessAndReleasesExactScheduleRevision() {
        UUID caseId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        SurgeryAuditActor recorder = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = newPreopCase(caseId, recorder);
        ReadinessSnapshot readiness = ReadinessSnapshot.evaluate(UUID.randomUUID(), caseId,
                true, true, true, true, true, true, true, REQUESTED_AT.plusSeconds(2),
                java.util.Arrays.stream(SurgeryDependencyType.values())
                        .map(type -> new SurgeryDependencyRevision(type, UUID.randomUUID(), 1)).toList(),
                null);
        surgeryCase.markReady(readiness, recorder, CORRELATION_ID);
        surgeryCase.finalizeSchedule(recorder, CORRELATION_ID, REQUESTED_AT.plusSeconds(3));
        UUID scheduleId = UUID.randomUUID();
        SurgerySchedule schedule = new SurgerySchedule(scheduleId, caseId, 4, UUID.randomUUID(),
                NOW.plusSeconds(3600), NOW.plusSeconds(5400), List.of(new SurgeryTeamAssignment(
                UUID.randomUUID(), SurgeryTeamRole.PRIMARY_SURGEON)));
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.NEW, receiptId, null, null));
        when(cases.lockById(caseId)).thenReturn(Optional.of(surgeryCase));
        when(consents.findByCaseId(caseId)).thenReturn(List.of());
        when(schedules.findByCaseId(caseId)).thenReturn(Optional.of(schedule));
        when(clock.now()).thenReturn(NOW);

        var outcome = service.sign(new ManageSurgeryConsentUseCase.SignCommand(
                caseId, 3, SurgeryConsentType.ANESTHESIA, UUID.randomUUID(),
                SurgeryConsentSignerType.PATIENT, null, "sign-scheduled", recorder, CORRELATION_ID));

        assertThat(outcome.caseRevision()).isEqualTo(surgeryCase.getRevision());
        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(surgeryCase.getReadinessSnapshot()).isNull();
        verify(reservations).release(caseId, scheduleId, 4, NOW);
        verify(cases).save(surgeryCase, 3);
    }

    private static SurgeryCase newPreopCase(UUID caseId, SurgeryAuditActor actor) {
        SurgeryCase surgeryCase = SurgeryCase.create(caseId, UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PROC-001",
                "Clinical indication", SurgeryPriority.ROUTINE, REQUESTED_AT, actor, CORRELATION_ID);
        surgeryCase.beginPreop(actor, CORRELATION_ID, REQUESTED_AT.plusSeconds(1));
        return surgeryCase;
    }

    private static SurgeryCase inProgressCase(UUID caseId, SurgeryAuditActor actor) {
        SurgeryCase surgeryCase = newPreopCase(caseId, actor);
        Instant readyAt = REQUESTED_AT.plusSeconds(2);
        ReadinessSnapshot readiness = ReadinessSnapshot.evaluate(UUID.randomUUID(), caseId,
                true, true, true, true, true, true, true, readyAt,
                java.util.Arrays.stream(SurgeryDependencyType.values())
                        .map(type -> new SurgeryDependencyRevision(type, UUID.randomUUID(), 1)).toList(),
                null);
        surgeryCase.markReady(readiness, actor, CORRELATION_ID);
        surgeryCase.finalizeSchedule(actor, CORRELATION_ID, readyAt.plusSeconds(1));
        surgeryCase.start(readiness, actor, CORRELATION_ID, readyAt.plusSeconds(2));
        return surgeryCase;
    }
}
