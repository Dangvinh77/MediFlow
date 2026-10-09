package com.mediflow.report.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.BillingCashReceiptMapper;
import com.mediflow.report.application.port.out.CashReceiptStorePort;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;

class CashReceiptApplicationServiceTest {
    private final CashReceiptStorePort store = mock(CashReceiptStorePort.class);
    private final BillingCashReceiptMapper mapper = new BillingCashReceiptMapper(ZoneId.of("Asia/Bangkok"));
    private final com.mediflow.report.application.port.in.ApplyCashRefundUseCase refunds = mock(com.mediflow.report.application.port.in.ApplyCashRefundUseCase.class);
    private final CashReceiptApplicationService service = new CashReceiptApplicationService(store, mapper, refunds);

    @Test
    void apply_newReceipt_recordsBeforeBothScopesInStableOrder() throws Exception {
        var event = fixture();
        var receipt = mapper.map(event);
        when(store.record(event, receipt)).thenReturn(true);
        service.apply(event);
        var order = inOrder(store);
        order.verify(store).record(event, receipt);
        order.verify(store).incrementGrossReceipts(receipt, null);
        order.verify(store).incrementGrossReceipts(receipt, receipt.departmentId());
        verifyNoMoreInteractions(store);
    }

    @Test
    void apply_duplicateReceipt_doesNotIncrementAnyScope() throws Exception {
        var event = fixture();
        service.apply(event);
        verify(store).record(event, mapper.map(event));
        verifyNoMoreInteractions(store);
    }

    @Test
    void apply_invalidReceipt_hasNoPersistenceInteraction() throws Exception {
        var event = fixture();
        var payload = new LinkedHashMap<>(event.payload());
        payload.remove("classification");
        assertThatThrownBy(() -> service.apply(new DecodedCareFinanceEvent(event.metadata(), payload)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }

    @Test
    void apply_recordConflict_isNotSwallowedOrCounted() throws Exception {
        var event = fixture();
        when(store.record(any(), any())).thenThrow(new IllegalStateException("source conflict"));
        assertThatThrownBy(() -> service.apply(event)).hasMessage("source conflict");
        verify(store).record(event, mapper.map(event));
        verifyNoMoreInteractions(store);
    }

    private static DecodedCareFinanceEvent fixture() throws Exception {
        return new CareFinanceEnvelopeDecoder(new ObjectMapper()).decode("payment.completed", Files.readAllBytes(
                Path.of("../billing-service/src/test/resources/contracts/ledger-v1/payment-service.json")));
    }
}
