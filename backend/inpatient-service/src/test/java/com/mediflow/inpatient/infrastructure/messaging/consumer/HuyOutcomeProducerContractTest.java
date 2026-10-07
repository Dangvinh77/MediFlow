package com.mediflow.inpatient.infrastructure.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.inpatient.application.dto.command.ExternalOrderFactCommand;
import com.mediflow.inpatient.application.dto.command.SurgeryCaseCreatedCommand;
import com.mediflow.inpatient.application.dto.command.SurgeryCompletedFactCommand;
import com.mediflow.inpatient.application.port.in.ReactToAdmissionReferralUseCase;
import com.mediflow.inpatient.application.port.in.ReactToDepositTopupUseCase;
import com.mediflow.inpatient.application.port.in.ReactToExternalOrderUseCase;
import com.mediflow.inpatient.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.inpatient.application.port.in.ReactToSettlementUseCase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.AmqpException;
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

    @Test
    void surgery_actualAdmissionCaseCreatedBytes_registerExactReference() throws Exception {
        receive("surgery.case.created", Path.of(
                "../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/"
                        + "surgery.case.created.admission.v1.json"));

        var captor = ArgumentCaptor.forClass(SurgeryCaseCreatedCommand.class);
        verify(orders).onSurgeryCaseCreated(captor.capture());
        assertThat(captor.getValue().maCaMo())
                .isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        assertThat(captor.getValue().maDotNoiTru())
                .isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000008"));
        assertThat(captor.getValue().maBenhNhan())
                .isEqualTo(UUID.fromString("00000000-0000-4000-8000-000000000003"));
        assertThat(captor.getValue().phienBanCa()).isZero();
        assertThat(captor.getValue().dauVanTai()).hasSize(64);
    }

    @ParameterizedTest
    @ValueSource(strings={"case.created","ready","completed","cancelled"})
    void surgery_actualOutpatientProducerBytes_areNotApplicableToInpatient(String type) throws Exception {
        receive("surgery." + type, Path.of(
                "../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery."
                        + type + ".outpatient.v1.json"));

        verifyNoInteractions(orders);
    }

    @Test
    void surgery_malformedOutpatientBytes_areRejectedBeforeClassification() throws Exception {
        Path file = Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/"
                + "surgery.completed.outpatient.v1.json");
        String malformed = Files.readString(file).replace(
                "    \"surgeryCaseId\": \"00000000-0000-4000-8000-000000000101\",\n", "");

        assertThatThrownBy(() -> consumer.receive(new Message(
                malformed.getBytes(StandardCharsets.UTF_8), new MessageProperties())))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("surgeryCaseId");
        verifyNoInteractions(orders);
    }

    @Test
    void surgery_outpatientWithAdmissionId_isRejectedBeforeClassification() throws Exception {
        Path file = Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/"
                + "surgery.completed.outpatient.v1.json");
        String malformed = Files.readString(file).replace(
                "\"admissionId\": null", "\"admissionId\": \"00000000-0000-4000-8000-000000000008\"");

        assertThatThrownBy(() -> consumer.receive(new Message(
                malformed.getBytes(StandardCharsets.UTF_8), new MessageProperties())))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("must not contain admissionId");
        verifyNoInteractions(orders);
    }

    @Test
    void surgery_outpatientWithNullRecordId_isNotApplicable() throws Exception {
        Path file = Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/"
                + "surgery.completed.outpatient.v1.json");
        String malformed = Files.readString(file).replace(
                "\"recordId\": \"00000000-0000-4000-8000-000000000109\"", "\"recordId\": null");

        assertThatCode(() -> consumer.receive(new Message(
                malformed.getBytes(StandardCharsets.UTF_8), new MessageProperties())))
                .doesNotThrowAnyException();
        verifyNoInteractions(orders);
    }

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
