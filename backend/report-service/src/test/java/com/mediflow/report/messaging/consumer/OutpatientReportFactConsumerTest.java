package com.mediflow.report.messaging.consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mediflow.report.application.port.in.ReceiveOutpatientReportFactUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class OutpatientReportFactConsumerTest {
    private final CareFinanceWirePort wire=mock(CareFinanceWirePort.class);
    private final ReceiveOutpatientReportFactUseCase receiver=mock(ReceiveOutpatientReportFactUseCase.class);
    private final OutpatientReportFactConsumer consumer=new OutpatientReportFactConsumer(wire,receiver);
    @Test void deferredFactIsNotAckDroppedAndErrorDoesNotExposePayload(){
        doThrow(new IllegalArgumentException("patient private data")).when(receiver).receive(any());
        assertThatThrownBy(()->consumer.consume(message())).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("Invalid, conflicting or deferred outpatient report contract").hasNoCause();
    }
    @Test void storageFailureRemainsRetryable(){
        var error=new org.springframework.dao.DataAccessResourceFailureException("offline");
        doThrow(error).when(receiver).receive(any());
        assertThatThrownBy(()->consumer.consume(message())).isSameAs(error);
    }
    private Message message(){var p=new MessageProperties();p.setReceivedRoutingKey("payment.completed");return new Message(new byte[]{1},p);}
}
