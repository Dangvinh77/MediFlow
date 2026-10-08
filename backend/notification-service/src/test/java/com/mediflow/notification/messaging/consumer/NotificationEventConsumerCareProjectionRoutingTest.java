package com.mediflow.notification.messaging.consumer;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.port.in.SendNotificationUseCase;
import com.mediflow.notification.application.service.NotificationTemplates;

/** 5 routing key mới (CONTRACT-CARE-PROJECTIONS-01) phải đi thẳng tới {@link CareProjectionWireHandler},
 * không chạm vào switch V1 phẳng hay {@link CarePaymentReceiptWireHandler}. */
class NotificationEventConsumerCareProjectionRoutingTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SendNotificationUseCase legacy = mock(SendNotificationUseCase.class);
    private final CarePaymentReceiptWireHandler careReceipts = mock(CarePaymentReceiptWireHandler.class);
    private final CareProjectionWireHandler careProjections = mock(CareProjectionWireHandler.class);
    private final NotificationEventConsumer consumer = new NotificationEventConsumer(
            legacy, new NotificationTemplates(), mapper, careReceipts, careProjections);

    @ParameterizedTest
    @ValueSource(strings = {"admission.deposit.requested", "admission.started", "admission.closed",
            "surgery.ready", "surgery.cancelled"})
    void routesExactBodyToCareProjectionHandler(String routingKey) throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        consumer.onMessage(new Message(body, propertiesFor(routingKey)));

        verify(careProjections).receive(eq(routingKey), eq(body));
        verifyNoInteractions(legacy, careReceipts);
    }

    @Test
    void careProjectionHandlerMissing_rejectsRatherThanSilentlyDropping() throws Exception {
        var noProjections = new NotificationEventConsumer(legacy, new NotificationTemplates(), mapper, careReceipts);
        var message = new Message("{}".getBytes(StandardCharsets.UTF_8), propertiesFor("surgery.ready"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> noProjections.onMessage(message))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
    }

    private MessageProperties propertiesFor(String routingKey) {
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey(routingKey);
        return properties;
    }
}
