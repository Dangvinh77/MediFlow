package com.mediflow.billing.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.*;
import com.mediflow.billing.application.port.out.SurgeryCancellationWirePort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SurgeryCancellationDispatchTest {
    @Test void enabled_dispatchesOriginalBytesExactlyOnce_neverCompatibilityRefunds() throws Exception {
        var old=mock(SurgeryChargeUseCase.class);
        var strict=mock(SurgeryCancellationConsumer.class);
        var dispatcher=new BillingEventConsumer(mock(AccrueFeeUseCase.class),mock(SagaCompensationUseCase.class),old,new ObjectMapper(),null,strict);
        var message=message(); dispatcher.onMessage(message);
        verify(strict).receive(same(message)); verifyNoInteractions(old);
    }
    @Test void strictDecodeFailure_rejectsWithoutCauseOrFallback() throws Exception {
        var wire=mock(SurgeryCancellationWirePort.class);
        var usecase=mock(ProcessSurgeryCancellationUseCase.class);
        var old=mock(SurgeryChargeUseCase.class);
        var strict=new SurgeryCancellationConsumer(wire,usecase);
        var message=message(); when(wire.decode("surgery.cancelled",message.getBody())).thenThrow(new IllegalArgumentException("private text"));
        var dispatcher=new BillingEventConsumer(mock(AccrueFeeUseCase.class),mock(SagaCompensationUseCase.class),old,new ObjectMapper(),null,strict);
        assertThatThrownBy(() -> dispatcher.onMessage(message)).isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class)
                .hasMessage("Invalid Surgery cancellation fact").hasNoCause();
        verifyNoInteractions(old,usecase);
    }
    @Test void transientStorageFailure_propagatesForBoundedRetry_noCompatibilityFallback() throws Exception {
        var strict=mock(SurgeryCancellationConsumer.class); var old=mock(SurgeryChargeUseCase.class); var message=message();
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("unavailable")).when(strict).receive(message);
        var dispatcher=new BillingEventConsumer(mock(AccrueFeeUseCase.class),mock(SagaCompensationUseCase.class),old,new ObjectMapper(),null,strict);
        assertThatThrownBy(() -> dispatcher.onMessage(message)).isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
        verifyNoInteractions(old);
    }
    private Message message() { var p=new MessageProperties(); p.setReceivedRoutingKey("surgery.cancelled"); return new Message(new byte[]{1,2,3},p); }
}
