package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.mediflow.report.application.dto.command.carefinance.RefundReplayInput;
import com.mediflow.report.application.dto.command.carefinance.RefundReplayInput.State;
import com.mediflow.report.application.dto.response.CashReplayProgress;
import com.mediflow.report.application.dto.response.CashReplayProgress.Status;
import com.mediflow.report.application.dto.response.RefundReplayProgress;
import com.mediflow.report.application.port.in.ReplayCashReceiptsUseCase;
import com.mediflow.report.application.port.out.RefundReplayStorePort;
import com.mediflow.report.domain.model.CashReceipt;
import com.mediflow.report.domain.model.CashRefund;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RefundReplayApplicationServiceTest {
    private final ReplayCashReceiptsUseCase receipts=mock(ReplayCashReceiptsUseCase.class);
    private final RefundReplayStorePort store=mock(RefundReplayStorePort.class);
    private final RefundReplayApplicationService service=new RefundReplayApplicationService(receipts,store);
    private final UUID id=UUID.randomUUID();
    @ParameterizedTest @ValueSource(ints={0,-1,501})
    void advance_invalidBatch_rejectsBeforeAnyPort(int batch) {
        assertThatThrownBy(()->service.advance(id,batch)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(receipts,store);
    }
    @Test void advance_unknownFormat_rejectsBeforeReceiptReplay() {
        when(store.lock(id)).thenReturn(new RefundReplayProgress(id,Status.BUILDING,1,0,0,0,2));
        assertThatThrownBy(()->service.advance(id,1)).hasMessageContaining("Unsupported refund replay format");
        verifyNoInteractions(receipts); verify(store,never()).pending(any(),anyInt());
    }
    @Test void advance_terminalGeneration_isNoEffect() {
        var completed=new RefundReplayProgress(id,Status.VERIFIED,1,1,0,0,1); when(store.lock(id)).thenReturn(completed);
        assertThat(service.advance(id,500)).isEqualTo(completed);
        verifyNoInteractions(receipts); verify(store,never()).restore(any(),any());
    }
    @Test void advance_receiptsNotVerified_neverUsesLiveOriginalToForceRefund() {
        var run=new RefundReplayProgress(id,Status.BUILDING,1,0,0,0,1); when(store.lock(id)).thenReturn(run);
        when(receipts.advance(id,1)).thenReturn(new CashReplayProgress(id,Status.BUILDING,2,1,1,1));
        assertThat(service.advance(id,1)).isEqualTo(run);
        verify(store,never()).pending(any(),anyInt()); verify(store,never()).original(any(),any());
    }
    @Test void advance_pendingSnapshot_restoresInventoryWithoutAttemptingRecovery() {
        ready(); var refund=refund(); var input=new RefundReplayInput(refund,State.PENDING,null,null,null);
        when(store.pending(id,500)).thenReturn(List.of(input)); when(store.progress(id)).thenReturn(new RefundReplayProgress(id,Status.BUILDING,1,1,1,0,1));
        var verified=new RefundReplayProgress(id,Status.VERIFIED,1,1,1,0,1); when(store.reconcile(id)).thenReturn(verified);
        assertThat(service.advance(id,500)).isEqualTo(verified); verify(store).restore(id,input); verify(store,never()).original(any(),any());
        verify(store).reconcile(id);
    }
    @Test void advance_appliedRefund_rechecksOriginalAndFrozenCumulativeBound() {
        ready(); var refund=refund(); var input=new RefundReplayInput(refund,State.APPLIED,CashReceipt.Classification.SERVICE_PAYMENT,LocalDate.of(2026,10,5),null);
        when(store.pending(id,500)).thenReturn(List.of(input));
        var original=new CashReceipt(refund.originalTransactionId(),null,UUID.randomUUID(),refund.accountId(),refund.patientId(),refund.departmentId(),
                refund.careEpisodeType(),refund.careEpisodeId(),CashReceipt.Classification.SERVICE_PAYMENT,new BigDecimal("100"),"VND","CASH",
                Instant.parse("2026-10-05T08:00:00Z"),LocalDate.of(2026,10,5),"Asia/Bangkok");
        when(store.original(id,refund.originalTransactionId())).thenReturn(original);
        when(store.acceptedRefundTotal(id,refund.originalTransactionId())).thenReturn(new BigDecimal("101"));
        assertThatThrownBy(()->service.advance(id,500)).hasMessageContaining("Invalid cash refund evidence");
        verify(store,never()).restore(any(),any());
    }
    private void ready() {
        when(store.lock(id)).thenReturn(new RefundReplayProgress(id,Status.BUILDING,1,0,0,0,1));
        when(receipts.advance(id,500)).thenReturn(new CashReplayProgress(id,Status.VERIFIED,1,1,1,1));
    }
    private static CashRefund refund() {
        return new CashRefund(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                "OUTPATIENT_VISIT",UUID.randomUUID(),new BigDecimal("20"),"VND",Instant.parse("2026-10-08T04:00:00Z"),LocalDate.of(2026,10,8),"Asia/Bangkok");
    }
}
