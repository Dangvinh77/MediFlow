package com.mediflow.surgery.application.service;

import com.mediflow.surgery.application.port.in.BeginPreopUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseRepositoryPort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.application.port.out.SurgeryCommandReceiptPort;
import com.mediflow.surgery.domain.model.CareEpisode;
import com.mediflow.surgery.domain.model.CareEpisodeType;
import com.mediflow.surgery.domain.model.SurgeryAuditActor;
import com.mediflow.surgery.domain.model.SurgeryCase;
import com.mediflow.surgery.domain.model.SurgeryPriority;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurgeryPreopApplicationServiceTest {

    private static final Instant REQUESTED_AT = Instant.parse("2026-09-29T01:00:00Z");
    private static final Instant NOW = REQUESTED_AT.plusSeconds(30);
    private static final String CORRELATION_ID = "preop-unit-test";

    @Mock private SurgeryCaseRepositoryPort cases;
    @Mock private SurgeryCommandReceiptPort receipts;
    @Mock private SurgeryClockPort clock;
    @InjectMocks private SurgeryPreopApplicationService service;

    @Test
    void beginPreop_newCommand_persistsTransitionAndReceiptAtServerTime() {
        UUID caseId = UUID.randomUUID();
        UUID receiptId = UUID.randomUUID();
        SurgeryAuditActor actor = SurgeryAuditActor.human(UUID.randomUUID(), UUID.randomUUID());
        SurgeryCase surgeryCase = newCase(caseId, actor);
        when(receipts.claim(any(), anyString())).thenReturn(new SurgeryCommandReceiptPort.Claim(
                SurgeryCommandReceiptPort.State.NEW, receiptId, null, null));
        when(cases.lockById(caseId)).thenReturn(Optional.of(surgeryCase));
        when(clock.now()).thenReturn(NOW);

        var outcome = service.begin(new BeginPreopUseCase.Command(
                caseId, 0, "begin-1", actor, CORRELATION_ID));

        assertThat(outcome.commandCode()).isEqualTo("BEGIN_PREOP");
        assertThat(outcome.state()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS.name());
        assertThat(outcome.caseRevision()).isEqualTo(1);
        assertThat(outcome.occurredAt()).isEqualTo(NOW);
        assertThat(surgeryCase.getStatus()).isEqualTo(SurgeryStatus.PREOP_IN_PROGRESS);
        assertThat(surgeryCase.getStatusHistory().getLast().occurredAt()).isEqualTo(NOW);
        verify(cases).save(surgeryCase, 0);

        ArgumentCaptor<byte[]> response = ArgumentCaptor.forClass(byte[].class);
        verify(receipts).complete(eq(receiptId), eq(caseId), eq("BEGIN_PREOP"), response.capture(), eq(NOW));
        assertThat(response.getValue()).isNotEmpty();
    }

    @Test
    void beginPreop_systemActor_rejectsBeforeClaimingReceipt() {
        BeginPreopUseCase.Command command = new BeginPreopUseCase.Command(
                UUID.randomUUID(), 0, "begin-system", SurgeryAuditActor.system("clinical-service"),
                CORRELATION_ID);

        assertThatThrownBy(() -> service.begin(command))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cases, receipts, clock);
    }

    private static SurgeryCase newCase(UUID caseId, SurgeryAuditActor actor) {
        return SurgeryCase.create(caseId, UUID.randomUUID(),
                new CareEpisode(CareEpisodeType.OUTPATIENT_VISIT, UUID.randomUUID(), null, UUID.randomUUID()),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PROC-001",
                "Clinical indication", SurgeryPriority.ROUTINE, REQUESTED_AT, actor, CORRELATION_ID);
    }
}
