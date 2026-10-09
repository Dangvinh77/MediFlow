package com.mediflow.pharmacy.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.port.in.ProjectAdmissionLifecycleUseCase;
import com.mediflow.pharmacy.application.port.out.AdmissionLifecycleWirePort;
import com.mediflow.pharmacy.domain.exception.PrescriptionRuleException;
import com.mediflow.pharmacy.infrastructure.messaging.AdmissionLifecycleDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AdmissionLifecycleConsumerTest {
    @ParameterizedTest
    @ValueSource(strings = {"admission.started", "discharge.medically.approved", "admission.closed"})
    void producerBytes_eachApprovedFactCallsTransactionalProjection(String key) throws Exception {
        var decoder = new AdmissionLifecycleDecoder(new ObjectMapper());
        var project = mock(ProjectAdmissionLifecycleUseCase.class);
        var message = message(key, fixture(key));
        new AdmissionLifecycleConsumer(decoder, project).receive(message);
        verify(project).project(decoder.decode(key, message.getBody()));
    }

    @Test
    void malformedInput_isPermanentAndNeverCallsProjection() {
        var decoder = mock(AdmissionLifecycleWirePort.class);
        var project = mock(ProjectAdmissionLifecycleUseCase.class);
        when(decoder.decode(any(), any())).thenThrow(new IllegalArgumentException("sensitive input"));
        assertThatThrownBy(() -> new AdmissionLifecycleConsumer(decoder, project).receive(message("admission.started", new byte[0])))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class).hasMessage("Invalid admission lifecycle contract")
                .hasNoCause();
        verifyNoInteractions(project);
    }

    @Test
    void identityConflict_isPermanentWithoutExposingSourceDetails() throws Exception {
        var decoder = new AdmissionLifecycleDecoder(new ObjectMapper());
        var project = mock(ProjectAdmissionLifecycleUseCase.class);
        doThrow(new PrescriptionRuleException("ADMISSION_FACT_CONFLICT", "sensitive input")).when(project).project(any());
        assertThatThrownBy(() -> new AdmissionLifecycleConsumer(decoder, project).receive(message("admission.started", fixture("admission.started"))))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class).hasMessage("Admission lifecycle identity or source conflict")
                .hasNoCause();
    }

    @Test
    void databaseFailure_propagatesForBoundedRetry() throws Exception {
        var project = mock(ProjectAdmissionLifecycleUseCase.class);
        var failure = new DataAccessResourceFailureException("database unavailable");
        doThrow(failure).when(project).project(any());
        assertThatThrownBy(() -> new AdmissionLifecycleConsumer(new AdmissionLifecycleDecoder(new ObjectMapper()), project)
                .receive(message("admission.started", fixture("admission.started")))).isSameAs(failure);
    }

    static byte[] fixture(String key) throws Exception {
        return Files.readAllBytes(Path.of("../inpatient-service/src/test/resources/contracts/" + key + ".v1.json"));
    }

    static Message message(String key, byte[] body) {
        var properties = new MessageProperties();
        properties.setReceivedRoutingKey(key);
        return new Message(body, properties);
    }
}
