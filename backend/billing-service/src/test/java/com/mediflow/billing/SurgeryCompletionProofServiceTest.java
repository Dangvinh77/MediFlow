package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.SurgeryChargeUseCase;
import com.mediflow.billing.application.port.out.SurgeryCompletionStorePort;
import com.mediflow.billing.application.service.SurgeryCompletionProofService;
import com.mediflow.billing.infrastructure.messaging.SurgeryCompletionDecoder;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SurgeryCompletionProofServiceTest {
    private final SurgeryCompletionStorePort store=mock(SurgeryCompletionStorePort.class);
    private final SurgeryChargeUseCase charges=mock(SurgeryChargeUseCase.class);
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    @Test void sourceAndChargeEffectsFollowOneOrderedUseCase()throws Exception {
        var command=new SurgeryCompletionDecoder(mapper).decode("surgery.completed",SurgeryCompletionDecoderTest.fixture("admission"));
        new SurgeryCompletionProofService(store,charges,Clock.systemUTC()).capture(command);
        var order=inOrder(store,charges);order.verify(store).lockAndFindReplay(command);
        order.verify(charges).onSurgeryCompleted(command.event());order.verify(store).save(command);
    }
    @Test void exactReplayNeverReconcilesOrStoresAnotherSource()throws Exception {
        var command=new SurgeryCompletionDecoder(mapper).decode("surgery.completed",SurgeryCompletionDecoderTest.fixture("admission"));
        when(store.lockAndFindReplay(command)).thenReturn(true);
        new SurgeryCompletionProofService(store,charges,Clock.systemUTC()).capture(command);
        verifyNoInteractions(charges);verify(store,never()).save(any());
    }
    @Test void failedChargeReconciliationCannotLeaveACompletionProof()throws Exception {
        var command=new SurgeryCompletionDecoder(mapper).decode("surgery.completed",SurgeryCompletionDecoderTest.fixture("admission"));
        doThrow(new IllegalStateException("price unavailable")).when(charges).onSurgeryCompleted(command.event());
        assertThatThrownBy(()->new SurgeryCompletionProofService(store,charges,Clock.systemUTC()).capture(command)).isInstanceOf(IllegalStateException.class);
        verify(store,never()).save(any());
    }
    @Test void futureRecordedFactRejectsBeforeAnyLocksOrEffects()throws Exception {
        var command=new SurgeryCompletionDecoder(mapper).decode("surgery.completed",SurgeryCompletionDecoderTest.fixture("admission"));
        var past=Clock.fixed(Instant.parse("2026-10-07T01:10:59Z"),ZoneOffset.UTC);
        assertThatThrownBy(()->new SurgeryCompletionProofService(store,charges,past).capture(command)).hasMessage("Future Surgery completion cannot close performed charges");
        verifyNoInteractions(store,charges);
    }
}
