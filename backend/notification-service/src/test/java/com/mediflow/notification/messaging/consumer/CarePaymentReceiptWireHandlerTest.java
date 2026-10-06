package com.mediflow.notification.messaging.consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.notification.application.dto.command.CarePaymentReceiptCommand;
import com.mediflow.notification.application.port.in.ReactToCarePaymentReceiptUseCase;
import com.mediflow.notification.application.port.in.SendNotificationUseCase;
import com.mediflow.notification.application.service.NotificationTemplates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class CarePaymentReceiptWireHandlerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReactToCarePaymentReceiptUseCase receipts = mock(ReactToCarePaymentReceiptUseCase.class);
    private final CarePaymentReceiptWireHandler handler = new CarePaymentReceiptWireHandler(mapper, receipts, true);

    @ParameterizedTest @ValueSource(strings = {"service", "deposit"})
    void consumesExactBillingProducerBytes(String name) throws Exception {
        handler.receive("payment.completed", fixture(name));
        var command = ArgumentCaptor.forClass(CarePaymentReceiptCommand.class);
        verify(receipts).receive(command.capture());
        assertThat(command.getValue().classification()).isEqualTo(name.equals("deposit") ? "ADMISSION_DEPOSIT" : "SERVICE_PAYMENT");
        assertThat(command.getValue().amount()).isEqualByComparingTo("100");
        assertThat(command.getValue().patientId()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(command.getValue().fingerprint()).hasSize(64);
    }

    @Test void existingQueueReaderDispatchesV1WithoutCallingLegacyOrCompetingListener() throws Exception {
        var legacy = mock(SendNotificationUseCase.class);
        var consumer = new NotificationEventConsumer(legacy, new NotificationTemplates(), mapper, handler);
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey("payment.completed");
        consumer.onMessage(new Message(fixture("deposit"), properties));
        verify(receipts).receive(any());
        verifyNoInteractions(legacy);
    }

    @Test void defaultOffNeverFallsBackToLegacyInvoicePayment() throws Exception {
        assertThatThrownBy(() -> new CarePaymentReceiptWireHandler(mapper, receipts, false)
                .receive("payment.completed", fixture("service"))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(receipts);
    }

    @ParameterizedTest @ValueSource(strings = {"transactionId", "accountId", "invoiceId", "patientId", "paymentRequestId", "careEpisodeId"})
    void missingExplicitIdentityIsRejected(String field) throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("service"));
        ((ObjectNode) root.path("payload")).remove(field);
        assertThatThrownBy(() -> handler.receive("payment.completed", mapper.writeValueAsBytes(root)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(receipts);
    }

    @Test void wrongProducerVersionClassificationOrMoneyIsRejectedBeforeEffect() throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("service"));
        root.put("producer", "pharmacy-service"); reject(root);
        root = (ObjectNode) mapper.readTree(fixture("service")); root.put("version", 4294967297L); reject(root);
        root = (ObjectNode) mapper.readTree(fixture("service")); ((ObjectNode) root.path("payload")).put("classification", "ADMISSION_DEPOSIT"); reject(root);
        root = (ObjectNode) mapper.readTree(fixture("service")); ((ObjectNode) root.path("payload")).put("totalAmount", "100"); reject(root);
        root = (ObjectNode) mapper.readTree(fixture("service")); ((ObjectNode) root.path("payload")).put("totalAmount", -1); reject(root);
        verifyNoInteractions(receipts);
    }

    @Test void malformedDuplicateKeysRejectButTransientDatabaseFailureIsRetriable() throws Exception {
        String valid = new String(fixture("service"), java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> handler.receive("payment.completed", (valid + " {}").getBytes()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> handler.receive("payment.completed", valid.replace("\"version\":1", "\"version\":0,\"version\":1").getBytes()))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("temporary database outage")).when(receipts).receive(any());
        assertThatThrownBy(() -> handler.receive("payment.completed", fixture("service")))
                .isInstanceOf(org.springframework.dao.TransientDataAccessResourceException.class);
    }
    private void reject(ObjectNode root) throws Exception {
        byte[] bytes = mapper.writeValueAsBytes(root);
        assertThatThrownBy(() -> handler.receive("payment.completed", bytes)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
    private static byte[] fixture(String name) throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/payment-" + name + ".json"));
    }
}
