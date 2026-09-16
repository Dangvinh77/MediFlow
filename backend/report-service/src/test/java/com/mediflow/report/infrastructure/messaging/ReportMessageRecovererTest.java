package com.mediflow.report.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class ReportMessageRecovererTest {

    @Test
    void recover_rejectsWithoutIncludingPayloadInExceptionMessage() {
        String sensitivePayload = "{\"patientName\":\"secret\",\"totalAmount\":999}";
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey("payment.completed");
        properties.setMessageId("message-1");
        properties.setCorrelationId("correlation-1");

        assertThatThrownBy(() -> new ReportMessageRecoverer().recover(
                new Message(sensitivePayload.getBytes(StandardCharsets.UTF_8), properties),
                new IllegalArgumentException("invalid payload")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("Report event rejected after retries")
                .hasMessageNotContaining("patientName")
                .hasMessageNotContaining("999");
    }
}
