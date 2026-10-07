package com.mediflow.inpatient.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.inpatient.application.dto.command.ExternalOrderFactCommand;
import com.mediflow.inpatient.application.dto.command.SurgeryCompletedFactCommand;
import com.mediflow.inpatient.application.port.in.ReactToAdmissionReferralUseCase;
import com.mediflow.inpatient.application.port.in.ReactToDepositTopupUseCase;
import com.mediflow.inpatient.application.port.in.ReactToExternalOrderUseCase;
import com.mediflow.inpatient.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.inpatient.application.port.in.ReactToSettlementUseCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Same checked-in producer bytes, not a copied consumer fixture or Java event dependency. */
class HuyOutcomeProducerContractTest {
    private final ReactToExternalOrderUseCase orders=mock(ReactToExternalOrderUseCase.class);
    private final InpatientEventConsumer consumer=new InpatientEventConsumer(new ObjectMapper(),
            mock(ReactToAdmissionReferralUseCase.class),mock(ReactToFinancialClearanceUseCase.class),
            mock(ReactToSettlementUseCase.class),mock(ReactToDepositTopupUseCase.class),orders);

    @ParameterizedTest @ValueSource(strings={"ready","completed","cancelled"})
    void surgery_actualAdmissionProducerBytes_reachExistingOrderPort(String type) throws Exception {
        var file=Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery."+type+".admission.v1.json");
        receive("surgery."+type,file);
        var captor=ArgumentCaptor.forClass(ExternalOrderFactCommand.class);
        verify(orders).onExternalOrderFact(captor.capture());
        assertThat(captor.getValue().maYLenhBenNgoai()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        assertThat(captor.getValue().maDotNoiTru()).isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000008"));
        if(captor.getValue() instanceof SurgeryCompletedFactCommand completed) assertThat(completed.tomTatBienChung()).isNull();
    }
    @Test void pharmacy_actualFilledProducerBytes_reachExistingOrderPort() throws Exception {
        receive("prescription.filled",Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.v1.json"));
        var captor=ArgumentCaptor.forClass(ExternalOrderFactCommand.class);
        verify(orders).onExternalOrderFact(captor.capture());
        assertThat(captor.getValue().maYLenhBenNgoai()).isEqualTo(UUID.fromString("10000000-0000-4000-8000-000000000001"));
    }
    private void receive(String key,Path file) throws Exception {
        var properties=new MessageProperties();properties.setReceivedRoutingKey(key);
        consumer.receive(new Message(Files.readAllBytes(file),properties));
    }
}
