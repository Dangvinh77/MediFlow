package com.mediflow.surgery.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mediflow.surgery.application.port.out.SurgeryEventPublisherPort;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class RabbitSurgeryEventPublisherAdapterUnitTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    @Test
    void publish_withoutConfirmTimesOutAsRetryableFailure() {
        RabbitSurgeryEventPublisherAdapter publisher =
                new RabbitSurgeryEventPublisherAdapter(rabbitTemplate, "mediflow.events", 1);

        assertThatThrownBy(() -> publisher.publish(message()))
                .isInstanceOf(SurgeryEventPublishException.class)
                .hasMessage("RabbitMQ confirm timed out")
                .satisfies(exception -> assertThat(
                        ((SurgeryEventPublishException) exception).returned()).isFalse());

        verify(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void publish_whenBrokerIsUnavailableMapsFailureWithoutLeakingBrokerMessage() {
        doThrow(new AmqpConnectException(new IOException("broker address is private")))
                .when(rabbitTemplate)
                .send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        RabbitSurgeryEventPublisherAdapter publisher =
                new RabbitSurgeryEventPublisherAdapter(rabbitTemplate, "mediflow.events", 100);

        assertThatThrownBy(() -> publisher.publish(message()))
                .isInstanceOf(SurgeryEventPublishException.class)
                .hasMessage("RabbitMQ publish failed")
                .satisfies(exception -> {
                    assertThat(((SurgeryEventPublishException) exception).returned()).isFalse();
                    assertThat(exception.getCause()).isInstanceOf(AmqpConnectException.class);
                });
    }

    private static SurgeryEventPublisherPort.OutgoingMessage message() {
        return new SurgeryEventPublisherPort.OutgoingMessage(
                UUID.randomUUID(), "surgery.ready", 1, "correlation-1", new byte[] {1, 2, 3});
    }
}
