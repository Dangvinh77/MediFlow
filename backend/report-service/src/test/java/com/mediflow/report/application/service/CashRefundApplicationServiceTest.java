package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.mapper.*;
import com.mediflow.report.application.port.out.CashRefundStorePort;
import com.mediflow.report.domain.model.*;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import org.junit.jupiter.api.Test;

class CashRefundApplicationServiceTest {
    private final CashRefundStorePort store = mock(CashRefundStorePort.class);
    private final BillingCashRefundMapper mapper = new BillingCashRefundMapper(ZoneId.of("Asia/Bangkok"));
    private final CashRefundApplicationService service = new CashRefundApplicationService(store, mapper);
    private final CareFinanceEnvelopeDecoder decoder = new CareFinanceEnvelopeDecoder(new ObjectMapper());
    @Test void apply_originalMissing_leavesDurablePendingNotInventedScope() throws Exception {
        var event = decoder.decode("payment.refunded", fixture("refund-service")); var refund = mapper.map(event);
        when(store.record(event, refund)).thenReturn(true); when(store.original(refund.originalTransactionId())).thenReturn(Optional.empty());
        service.apply(event);
        verify(store, never()).apply(any(), any()); verify(store, never()).appliedTotal(any());
        var order = inOrder(store); order.verify(store).lockAccount(refund.accountId());
        order.verify(store).lockOriginal(refund.originalTransactionId()); order.verify(store).record(event, refund);
    }
    @Test void apply_knownReceipt_validatesBoundBeforeEffects() throws Exception {
        var event = decoder.decode("payment.refunded", fixture("refund-service")); var refund = mapper.map(event); var original = receipt();
        when(store.record(event, refund)).thenReturn(true); when(store.original(refund.originalTransactionId())).thenReturn(Optional.of(original));
        when(store.appliedTotal(refund.originalTransactionId())).thenReturn(new BigDecimal("81"));
        assertThatThrownBy(() -> service.apply(event)).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
        verify(store, never()).apply(any(), any());
    }
    @Test void recover_earlyWrongPatient_quarantinesWithoutPoisoningValidReceipt() throws Exception {
        var event = decoder.decode("payment.refunded", fixture("refund-service")); var refund = mapper.map(event); var original = receipt();
        var bad = new CashRefund(refund.refundTransactionId(), refund.originalTransactionId(), refund.accountId(), UUID.randomUUID(), refund.departmentId(),
                refund.careEpisodeType(), refund.careEpisodeId(), refund.amount(), refund.currency(), refund.completedAt(), refund.businessDate(), refund.reportZone());
        when(store.original(original.transactionId())).thenReturn(Optional.of(original)); when(store.pending(original.transactionId(), 20)).thenReturn(List.of(bad));
        when(store.appliedTotal(original.transactionId())).thenReturn(BigDecimal.ZERO);
        service.recover(original.transactionId()); verify(store).reject(bad.refundTransactionId(), "CASH_REFUND_ORIGINAL_MISMATCH"); verify(store, never()).apply(any(), any());
    }
    @Test void apply_duplicate_doesNotApplyTwice() throws Exception {
        var event = decoder.decode("payment.refunded", fixture("refund-service")); service.apply(event);
        verify(store, never()).original(any()); verify(store, never()).apply(any(), any());
    }
    @Test void recover_acquiresAccountBeforeOriginalAndRereadsImmutableIdentity() throws Exception {
        var original = receipt(); when(store.original(original.transactionId())).thenReturn(Optional.of(original));
        service.recover(original.transactionId());
        var order = inOrder(store);
        order.verify(store).original(original.transactionId());
        order.verify(store).lockAccount(original.accountId());
        order.verify(store).lockOriginal(original.transactionId());
        order.verify(store).original(original.transactionId());
        order.verify(store).pending(original.transactionId(), 20);
    }
    @Test void recover_absentOriginalDoesNotInventAccountOrAcquireInvertedLocks() {
        var originalId = UUID.randomUUID(); when(store.original(originalId)).thenReturn(Optional.empty());
        service.recover(originalId);
        verify(store, never()).lockAccount(any()); verify(store, never()).lockOriginal(any());
        verify(store, never()).pending(any(), anyInt());
    }
    @Test void apply_newRefundAfterFinal_requiresExplicitEarnedCorrectionAndHasNoEffect() throws Exception {
        var event = decoder.decode("payment.refunded", fixture("refund-service"));
        var refund = mapper.map(event); var original = receipt();
        when(store.record(event, refund)).thenReturn(true);
        when(store.original(refund.originalTransactionId())).thenReturn(Optional.of(original));
        when(store.appliedTotal(refund.originalTransactionId())).thenReturn(BigDecimal.ZERO);
        when(store.finalized(refund.accountId())).thenReturn(true);
        assertThatThrownBy(() -> service.apply(event)).isInstanceOfSatisfying(
                com.mediflow.report.domain.exception.ReportRuleException.class,
                error -> assertThat(error.getCode()).isEqualTo("CASH_REFUND_FINAL_CORRECTION_UNSUPPORTED"));
        verify(store, never()).apply(any(), any());
    }
    @Test void recover_pendingRefundOnFinalAccount_quarantinesWithoutReversingEarned() throws Exception {
        var event = decoder.decode("payment.refunded", fixture("refund-service")); var refund = mapper.map(event); var original = receipt();
        when(store.original(original.transactionId())).thenReturn(Optional.of(original));
        when(store.pending(original.transactionId(), 20)).thenReturn(List.of(refund));
        when(store.appliedTotal(original.transactionId())).thenReturn(BigDecimal.ZERO);
        when(store.finalized(original.accountId())).thenReturn(true);
        service.recover(original.transactionId());
        verify(store).reject(refund.refundTransactionId(), "CASH_REFUND_FINAL_CORRECTION_UNSUPPORTED");
        verify(store, never()).apply(any(), any());
    }
    @Test void apply_existingRefundRetry_doesNotReadFinalStatusOrMutateAgain() throws Exception {
        var event = decoder.decode("payment.refunded", fixture("refund-service"));
        service.apply(event);
        verify(store, never()).finalized(any()); verify(store, never()).apply(any(), any());
    }
    private CashReceipt receipt() throws Exception { return new BillingCashReceiptMapper(ZoneId.of("Asia/Bangkok")).map(decoder.decode("payment.completed", fixture("payment-service"))); }
    private static byte[] fixture(String name) throws Exception { return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/" + name + ".json")); }
}
