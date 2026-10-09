package com.mediflow.report.messaging.consumer;

import com.mediflow.common.exception.BusinessRuleException;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.port.in.ReceiveOperationalReportFactUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OperationalReportFactConsumerTest {
    private final CareFinanceWirePort decoder = mock(CareFinanceWirePort.class);
    private final ReceiveOperationalReportFactUseCase receiver = mock(ReceiveOperationalReportFactUseCase.class);
    private final OperationalReportFactConsumer consumer = new OperationalReportFactConsumer(decoder, receiver);
    private final byte[] body = {1, 2, 3};

    @Test void consume_validFactReturnsOnlyAfterReceiverReturns() {
        var event = mock(DecodedCareFinanceEvent.class);
        when(decoder.decode("surgery.completed", body)).thenReturn(event);
        consumer.consume(message());
        var order = inOrder(decoder, receiver);
        order.verify(decoder).decode("surgery.completed", body); order.verify(receiver).receive(event);
    }
    @Test void consume_malformedRejectsWithoutCallingReceiverOrLeakingPayload() {
        when(decoder.decode(anyString(), any())).thenThrow(new ReportEventValidationException("patient secret"));
        assertSafeReject(); verifyNoInteractions(receiver);
    }
    @Test void consume_conflictRejectsWithoutLeakingDomainValues() {
        doThrow(new BusinessRuleException("CONFLICT", "patient secret")).when(receiver).receive(any());
        assertSafeReject();
    }
    @Test void consume_databaseOutagePropagatesForBoundedRetry() {
        var failure = new DataAccessResourceFailureException("offline");
        doThrow(failure).when(receiver).receive(any());
        assertThatThrownBy(() -> consumer.consume(message())).isSameAs(failure);
    }
    private void assertSafeReject() {
        assertThatThrownBy(() -> consumer.consume(message())).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("Invalid or conflicting operational report contract").hasNoCause();
    }
    private Message message() {
        var properties = new MessageProperties(); properties.setReceivedRoutingKey("surgery.completed");
        return new Message(body, properties);
    }
}
