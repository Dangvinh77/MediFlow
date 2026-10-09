package com.mediflow.report.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand;
import com.mediflow.report.application.port.in.ApplyOperationalContributionUseCase;
import com.mediflow.report.application.port.in.ProjectAdmissionReportEvidenceUseCase;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OperationalReportFactReceiverTest {
    private final ApplyOperationalContributionUseCase operations = mock(ApplyOperationalContributionUseCase.class);
    private final ProjectAdmissionReportEvidenceUseCase admissions = mock(ProjectAdmissionReportEvidenceUseCase.class);
    private final OperationalReportFactReceiver receiver = new OperationalReportFactReceiver(operations, admissions, ZoneId.of("Asia/Bangkok"));
    private final CareFinanceEnvelopeDecoder decoder = new CareFinanceEnvelopeDecoder(new ObjectMapper());

    @ParameterizedTest
    @CsvSource({
            "medicalrecord.completed,clinical-service,contracts/medicalrecord.completed.v1.json,1",
            "lab.result.created,lab-service,contracts/lab.result.created.v1.json,1",
            "lab.result.created,lab-service,contracts/lab.result.created.admission.v1.json,1",
            "prescription.filled,pharmacy-service,contracts/care-finance-v1/prescription.filled.v1.json,2",
            "surgery.completed,surgery-service,contracts/surgery-outcomes-v1/surgery.completed.admission.v1.json,2",
            "surgery.cancelled,surgery-service,contracts/surgery-outcomes-v1/surgery.cancelled.admission.v1.json,1"
    })
    void receive_actualProducerBytesMapToOnlyExpectedKernel(String key, String service, String file, int count) throws Exception {
        var event = decoder.decode(key, Files.readAllBytes(Path.of("../" + service + "/src/test/resources/" + file)));
        receiver.receive(event);
        var command = ArgumentCaptor.forClass(ApplyOperationalContributionCommand.class);
        verify(operations).apply(command.capture());
        assertThat(command.getValue().event()).isEqualTo(event);
        assertThat(command.getValue().contributions()).hasSize(count).allSatisfy(fact -> {
            assertThat(fact.sourceId()).isEqualTo(event.metadata().sourceId());
            assertThat(fact.sourceRevision()).isOne();
        });
        verifyNoInteractions(admissions);
    }

    @Test
    void receive_administrativeCloseRemainsEvidenceNotMedicalDischarge() throws Exception {
        String key = "admission.closed";
        var event = decoder.decode(key, Files.readAllBytes(Path.of("../inpatient-service/src/test/resources/contracts/" + key + ".v1.json")));
        receiver.receive(event);
        verify(admissions).project(event);
        verifyNoInteractions(operations);
    }
    @Test void receive_startValidatesEvidenceBeforeCountingExactlyOneAdmission() throws Exception {
        var event = decoder.decode("admission.started", Files.readAllBytes(Path.of(
                "../inpatient-service/src/test/resources/contracts/admission.started.v1.json")));
        receiver.receive(event);
        var command = ArgumentCaptor.forClass(ApplyOperationalContributionCommand.class);
        var order = inOrder(admissions, operations); order.verify(admissions).project(event); order.verify(operations).apply(command.capture());
        assertThat(command.getValue().contributions()).singleElement().satisfies(fact -> {
            assertThat(fact.metric()).isEqualTo(com.mediflow.report.domain.model.OperationalContribution.Metric.ADMISSIONS);
            assertThat(fact.sourceId()).isEqualTo(event.metadata().sourceId()); assertThat(fact.value()).isEqualByComparingTo("1");
        });
    }
    @Test void receive_conflictingAdmissionCannotIncrementCounter() throws Exception {
        var event = decoder.decode("admission.started", Files.readAllBytes(Path.of(
                "../inpatient-service/src/test/resources/contracts/admission.started.v1.json")));
        doThrow(new IllegalArgumentException("conflict")).when(admissions).project(event);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> receiver.receive(event)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(operations);
    }

    @Test void receive_financialEnvelopeIsNotAnOperationalFact() throws Exception {
        var event = decoder.decode("payment.completed", Files.readAllBytes(Path.of(
                "../billing-service/src/test/resources/contracts/ledger-v1/payment-service.json")));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> receiver.receive(event)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(operations, admissions);
    }
}
