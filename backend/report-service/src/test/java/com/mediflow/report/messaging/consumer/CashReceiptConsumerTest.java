package com.mediflow.report.messaging.consumer;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.port.in.ApplyCashReceiptUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CashReceiptConsumerTest {
    private final CareFinanceWirePort decoder = mock(CareFinanceWirePort.class);
    private final ApplyCashReceiptUseCase receipts = mock(ApplyCashReceiptUseCase.class);
    private final CashReceiptConsumer consumer = new CashReceiptConsumer(decoder, receipts);
    private final byte[] body = {1, 2, 3};

    @Test void consume_validFactReturnsAfterTransactionalKernel() {
        var event = mock(DecodedCareFinanceEvent.class);
        when(decoder.decode("payment.completed", body)).thenReturn(event);
        consumer.consume(message());
        var order = inOrder(decoder, receipts);
        order.verify(decoder).decode("payment.completed", body);
        order.verify(receipts).apply(event);
    }
    @Test void consume_malformedRejectsWithoutCallingKernelOrLeakingValues() {
        when(decoder.decode(anyString(), any())).thenThrow(new ReportEventValidationException("patient secret"));
        assertSafeReject(); verifyNoInteractions(receipts);
    }
    @Test void consume_conflictIsPermanentAndRedacted() {
        doThrow(new BusinessRuleException("CONFLICT", "patient secret")).when(receipts).apply(any());
        assertSafeReject();
    }
    @Test void consume_invalidBusinessTimeIsPermanentAndRedacted() {
        doThrow(new DateTimeParseException("patient secret", "patient secret", 0)).when(receipts).apply(any());
        assertSafeReject();
    }
    @Test void consume_unsupportedClassificationIsPermanent() {
        doThrow(new IllegalArgumentException("unsupported secret")).when(receipts).apply(any());
        assertSafeReject();
    }
    @Test void consume_storageFailurePropagatesForBoundedRetry() {
        var failure = new DataAccessResourceFailureException("offline");
        doThrow(failure).when(receipts).apply(any());
        assertThatThrownBy(() -> consumer.consume(message())).isSameAs(failure);
    }
    private void assertSafeReject() {
        assertThatThrownBy(() -> consumer.consume(message())).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("Invalid or conflicting cash receipt contract").hasNoCause();
    }
    private Message message() {
        var properties = new MessageProperties(); properties.setReceivedRoutingKey("payment.completed");
        return new Message(body, properties);
    }
}
