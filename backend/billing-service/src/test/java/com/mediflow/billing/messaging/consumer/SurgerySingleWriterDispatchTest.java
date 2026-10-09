package com.mediflow.billing.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.billing.application.port.in.AccrueFeeUseCase;
import com.mediflow.billing.application.port.in.IssueSurgeryChargeUseCase;
import com.mediflow.billing.application.port.in.SagaCompensationUseCase;
import com.mediflow.billing.application.port.in.SurgeryChargeUseCase;
import com.mediflow.billing.infrastructure.messaging.SurgeryChargeDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class SurgerySingleWriterDispatchTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final SurgeryChargeDecoder decoder = new SurgeryChargeDecoder(mapper);
    private final IssueSurgeryChargeUseCase issuer = mock(IssueSurgeryChargeUseCase.class);
    private final SurgeryChargeUseCase compatibility = mock(SurgeryChargeUseCase.class);
    private final AccrueFeeUseCase fees = mock(AccrueFeeUseCase.class);
    private final SagaCompensationUseCase saga = mock(SagaCompensationUseCase.class);
    private final BillingEventConsumer consumer = new BillingEventConsumer(fees, saga, compatibility,
            mapper, new SurgeryChargeConsumer(decoder, issuer));

    @ParameterizedTest @ValueSource(strings = {"admission", "outpatient"})
    void created_enabledIssuer_receivesOriginalProducerBytesWithNoSecondWriter(String context) throws Exception {
        byte[] body = fixture("surgery.case.created." + context + ".v1.json");
        consumer.onMessage(message("surgery.case.created", body));
        verify(issuer).issue(decoder.decode("surgery.case.created", body));
        verifyNoInteractions(compatibility, fees, saga);
    }

    @Test
    void created_malformedEnvelope_neverFallsBackToPermissiveCompatibilityReader() throws Exception {
        var root = mapper.readTree(fixture("surgery.case.created.admission.v1.json"));
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("version", 999);
        assertThatThrownBy(() -> consumer.onMessage(message("surgery.case.created", mapper.writeValueAsBytes(root))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(issuer, compatibility, fees, saga);
    }

    @Test
    void created_storageFailure_propagatesWithoutFallbackOrSecondCharge() throws Exception {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic storage failure"))
                .when(issuer).issue(any());
        assertThatThrownBy(() -> consumer.onMessage(message("surgery.case.created",
                fixture("surgery.case.created.admission.v1.json"))))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        verify(issuer, times(1)).issue(any());
        verifyNoInteractions(compatibility, fees, saga);
    }

    @Test
    void completedAndCancelled_enabledIssuer_preserveOwnerOutcomeHandlers() throws Exception {
        consumer.onMessage(message("surgery.completed", fixture("surgery.completed.admission.v1.json")));
        consumer.onMessage(message("surgery.cancelled", fixture("surgery.cancelled.admission.v1.json")));
        verify(compatibility).onSurgeryCompleted(any());
        verify(compatibility).onSurgeryCancelled(any());
        verifyNoInteractions(issuer, fees, saga);
    }

    @Test
    void handlers_onlyOnePhysicalListenerForBillingQueue() {
        var methods = java.util.Arrays.stream(BillingEventConsumer.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(org.springframework.amqp.rabbit.annotation.RabbitListener.class)).toList();
        assertThat(methods).hasSize(1);
        assertThat(methods.getFirst().getAnnotation(org.springframework.amqp.rabbit.annotation.RabbitListener.class).queues())
                .containsExactly("billing.q");
    }

    private static Message message(String key, byte[] body) {
        var properties = new MessageProperties(); properties.setReceivedRoutingKey(key);
        return new Message(body, properties);
    }
    private static byte[] fixture(String name) throws Exception {
        Path directory = Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1");
        if (!Files.exists(directory)) directory = Path.of("backend/surgery-service/src/test/resources/contracts/surgery-outcomes-v1");
        return Files.readAllBytes(directory.resolve(name));
    }
}
