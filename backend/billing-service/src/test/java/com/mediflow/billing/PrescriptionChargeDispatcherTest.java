package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.in.*;
import com.mediflow.billing.infrastructure.messaging.PrescriptionChargeDecoder;
import com.mediflow.billing.infrastructure.messaging.consumer.PrescriptionChargeConsumer;
import com.mediflow.billing.messaging.consumer.BillingEventConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrescriptionChargeDispatcherTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AccrueFeeUseCase compatibility = mock(AccrueFeeUseCase.class);
    private final SagaCompensationUseCase compensation = mock(SagaCompensationUseCase.class);
    private final IssuePrescriptionChargeUseCase issuer = mock(IssuePrescriptionChargeUseCase.class);
    private final ApplyPrescriptionTerminalUseCase terminal = mock(ApplyPrescriptionTerminalUseCase.class);
    private final BillingEventConsumer dispatcher = new BillingEventConsumer(compatibility, compensation, mock(SurgeryChargeUseCase.class), mapper,
            null, null, new PrescriptionChargeConsumer(mapper, new PrescriptionChargeDecoder(mapper), issuer, terminal));
    @Test void dispatch_v1_usesOneWriterAndNoCompatibilityFallback() throws Exception {
        dispatcher.onMessage(message("prescription.created", PrescriptionChargeContractTest.fixture()));
        verify(issuer).issue(any()); verifyNoInteractions(compatibility, compensation);
    }
    @Test void dispatch_v0_preservesCompatibility() throws Exception {
        dispatcher.onMessage(message("prescription.created", "{\"eventId\":\"10000000-0000-4000-8000-000000000001\"}".getBytes()));
        verify(compatibility).onPrescriptionCreated(any()); verifyNoInteractions(issuer, compensation);
    }
    @Test void dispatch_malformedV1_noFallbackOrMoneyEffects() throws Exception {
        var root = mapper.readTree(PrescriptionChargeContractTest.fixture()); ((com.fasterxml.jackson.databind.node.ObjectNode)root).put("version", 0);
        assertThatThrownBy(() -> dispatcher.onMessage(message("prescription.created", mapper.writeValueAsBytes(root)))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(compatibility, compensation, issuer);
    }
    @Test void dispatch_v1Terminal_noLegacyCompensation() throws Exception {
        byte[] terminal = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.cancelled.v1.json"));
        dispatcher.onMessage(message("prescription.cancelled", terminal));
        verify(this.terminal).apply(any());
        verifyNoInteractions(compatibility, compensation, issuer);
    }
    private Message message(String key, byte[] body) {
        var properties = new MessageProperties(); properties.setReceivedRoutingKey(key); return new Message(body, properties);
    }
}
