package com.mediflow.report.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.port.in.*;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutpatientReportFactReceiverTest {
    private final ApplyOperationalContributionUseCase operations=mock(ApplyOperationalContributionUseCase.class);
    private final ApplyCashReceiptUseCase cash=mock(ApplyCashReceiptUseCase.class);
    private final ApplyCashRefundUseCase refunds=mock(ApplyCashRefundUseCase.class);
    private final OutpatientReportFactReceiver receiver=new OutpatientReportFactReceiver(operations,cash,refunds,ZoneId.of("Asia/Bangkok"));
    private final ObjectMapper json=new ObjectMapper();
    private final CareFinanceEnvelopeDecoder decoder=new CareFinanceEnvelopeDecoder(json);
    @ParameterizedTest @CsvSource({
        "lab.result.created,lab-service,contracts/lab.result.created.v1.json",
        "prescription.filled,pharmacy-service,contracts/care-finance-v1/prescription.filled.outpatient.v1.json"
    }) void actualOutpatientProducerBytesUseOnlyOperationalKernel(String key,String service,String fixture)throws Exception{
        var event=decoder.decode(key,bytes(service,fixture));receiver.receive(event);
        verify(operations).apply(argThat(c->c.event().equals(event)&&c.contributions().stream()
                .allMatch(f->"OUTPATIENT_VISIT".equals(f.careEpisodeType()))));
        verifyNoInteractions(cash,refunds);
    }
    @Test void actualBillingCashAndRefundUseExistingOriginalLinkedKernels()throws Exception{
        var receipt=decoder.decode("payment.completed",bytes("billing-service","contracts/ledger-v1/payment-service.json"));
        var refund=decoder.decode("payment.refunded",bytes("billing-service","contracts/ledger-v1/refund-service.json"));
        receiver.receive(refund);receiver.receive(receipt); // Broker ordering is irrelevant to source selection.
        verify(cash).apply(receipt);verify(refunds).apply(refund);verifyNoInteractions(operations);
    }
    @ParameterizedTest @CsvSource({
        "medicalrecord.completed,clinical-service,contracts/medicalrecord.completed.v1.json",
        "lab.result.created,lab-service,contracts/lab.result.created.admission.v1.json",
        "prescription.filled,pharmacy-service,contracts/care-finance-v1/prescription.filled.v1.json",
        "payment.completed,billing-service,contracts/ledger-v1/payment-deposit.json",
        "payment.refunded,billing-service,contracts/ledger-v1/refund-deposit.json"
    }) void actualAdmissionFixturesAreDeferredBeforeAnyMvpEffect(String key,String service,String fixture)throws Exception{
        var event=decoder.decode(key,bytes(service,fixture));
        assertThatThrownBy(()->receiver.receive(event)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(operations,cash,refunds);
    }
    @Test void localOutpatientClinicalVariantCountsCompletionNeverRecordCreated()throws Exception{
        // LOCAL VARIANT ONLY: do not claim the Clinical owner has accepted this outpatient fixture.
        var root=json.readTree(bytes("clinical-service","contracts/medicalrecord.completed.v1.json"));
        var payload=(com.fasterxml.jackson.databind.node.ObjectNode)root.get("payload");
        payload.put("disposition","PRESCRIPTION");payload.put("admissionRequired",false);
        receiver.receive(decoder.decode("medicalrecord.completed",json.writeValueAsBytes(root)));
        verify(operations).apply(argThat(c->c.contributions().size()==1&&c.contributions().getFirst().metric()
                ==com.mediflow.report.domain.model.OperationalContribution.Metric.COMPLETED_VISITS));
        verifyNoInteractions(cash,refunds);
    }
    // Courses, handovers, notes and amendments publish nothing; a redelivered completion carries the same source key.
    @Test void sameRecordCompletionDeliveredTwiceCountsOnce()throws Exception{
        var root=json.readTree(bytes("clinical-service","contracts/medicalrecord.completed.v1.json"));
        var payload=(com.fasterxml.jackson.databind.node.ObjectNode)root.get("payload");
        payload.put("disposition","PRESCRIPTION");payload.put("admissionRequired",false);
        var event=decoder.decode("medicalrecord.completed",json.writeValueAsBytes(root));
        receiver.receive(event);receiver.receive(event);
        var commands=org.mockito.ArgumentCaptor.forClass(com.mediflow.report.application.dto.command.carefinance.ApplyOperationalContributionCommand.class);
        verify(operations,times(2)).apply(commands.capture());
        var first=commands.getAllValues().get(0).contributions().getFirst();
        var second=commands.getAllValues().get(1).contributions().getFirst();
        assertThat(second.eventId()).isEqualTo(first.eventId());
        assertThat(second.sourceId()).isEqualTo(first.sourceId())
                .isEqualTo(java.util.UUID.fromString(payload.get("recordId").asText()));
    }
    private byte[] bytes(String service,String file)throws Exception{return Files.readAllBytes(Path.of("../"+service+"/src/test/resources/"+file));}
}
