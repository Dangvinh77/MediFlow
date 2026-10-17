package com.mediflow.billing.application.event;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.port.out.*;
import com.mediflow.billing.application.service.LedgerPaymentService;
import com.mediflow.billing.application.service.PrescriptionChargeService;
import com.mediflow.billing.domain.model.*;
import com.mediflow.billing.infrastructure.messaging.PrescriptionChargeDecoder;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual issuer/payment services produce the shared fixtures; only generated identities normalize. */
class PrescriptionFinanceProducerFixtureTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    @Test void issuerThenTwoPayments_matchesInvoiceReceiptsAndSingleGrant() throws Exception {
        var notice = fixture("invoice-prescription"); var p = notice.path("payload");
        var wire = Files.readAllBytes(Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.created.outpatient.v1.json"));
        var c = new PrescriptionChargeDecoder(mapper).decode("prescription.created", wire);
        UUID accountId = id(p, "accountId"), requestId = id(p, "paymentRequestId"), invoiceId = id(p, "invoiceId");
        Instant now = Instant.parse(notice.path("occurredAt").asText()); var clock = Clock.fixed(now, ZoneOffset.UTC);
        var issued = new PrescriptionChargeRepositoryPort.IssuedCharge(UUID.randomUUID(), accountId, requestId, invoiceId);
        var sources = mock(PrescriptionChargeRepositoryPort.class); var events = mock(LedgerEventPort.class);
        when(sources.lockRecordedSource(c)).thenReturn(Optional.empty()); when(sources.openAndLockExactAccount(c)).thenReturn(accountId);
        when(sources.saveChargeAndRequest(eq(c), eq(accountId), any(), eq(now))).thenReturn(issued);
        new PrescriptionChargeService(sources, events, clock, mock(PrescriptionPricePolicyPort.class)).issue(c);
        var capture = ArgumentCaptor.forClass(LedgerIntegrationEvent.class);
        verify(events).appendForOutpatientRequest(eq(accountId), eq(requestId), capture.capture());
        compare(capture.getValue(), notice, false);
        clearInvocations(events);
        var account = BillingAccount.restore(accountId, c.patientId(), c.departmentId(), CareEpisodeType.OUTPATIENT_VISIT,
                c.careEpisodeId(), AccountStatus.OPEN, "VND", 0, now, null, null, now, now);
        var request = PaymentRequest.restore(requestId, invoiceId, accountId, PaymentRequestPurpose.PRESCRIPTION,
                PaymentRequestStatus.PENDING, c.totalAmount(), "VND", null, null, now, null);
        var target = new ClearanceTarget(null, null, List.of(), c.prescriptionId(), null, null);
        var repository = mock(LedgerPaymentRepositoryPort.class);
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.lockRequest(requestId)).thenReturn(new LedgerPaymentRepositoryPort.PaymentContext(account, request, target, BigDecimal.ZERO));
        var grantFixture = fixture("clearance-prescription-issued");
        when(repository.saveClearance(any())).thenAnswer(call -> {
            FinancialClearance grant = call.getArgument(0);
            return FinancialClearance.restore(id(grantFixture.path("payload"), "clearanceId"), accountId, requestId, invoiceId, c.patientId(),
                    ClearancePurpose.PRESCRIPTION, CareEpisodeType.OUTPATIENT_VISIT, c.careEpisodeId(), null, null, List.of(), c.prescriptionId(),
                    null, null, c.totalAmount(), "VND", "CASH", false, null, now, null);
        });
        var payments = new LedgerPaymentService(repository, events, Mappers.getMapper(LedgerPaymentMapper.class), clock);
        payments.complete(requestId, new CompleteLedgerPaymentRequest("first", new BigDecimal("750.00"), "VND", PaymentMethod.CASH, null), UUID.randomUUID(), c.correlationId());
        verify(repository, never()).saveClearance(any());
        when(repository.lockRequest(requestId)).thenReturn(new LedgerPaymentRepositoryPort.PaymentContext(account, request, target, new BigDecimal("750.00")));
        payments.complete(requestId, new CompleteLedgerPaymentRequest("last", new BigDecimal("1250.00"), "VND", PaymentMethod.CASH, null), UUID.randomUUID(), c.correlationId());
        var paid = ArgumentCaptor.forClass(LedgerIntegrationEvent.class);
        verify(events, times(3)).appendForOutpatientRequest(eq(accountId), eq(requestId), paid.capture());
        compare(paid.getAllValues().get(0), fixture("payment-prescription-first"), true);
        compare(paid.getAllValues().get(1), fixture("payment-prescription-last"), true);
        compare(paid.getAllValues().get(2), grantFixture, false);
    }
    private void compare(LedgerIntegrationEvent event, JsonNode expected, boolean generatedTransaction) {
        var actual = (ObjectNode) mapper.valueToTree(event); actual.set("eventId", expected.get("eventId"));
        if (generatedTransaction) ((ObjectNode)actual.get("payload")).set("transactionId", expected.path("payload").get("transactionId"));
        assertThat(actual).isEqualTo(expected);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"first", "remainder", "last"})
    void actualRefundService_matchesOriginalOnlyPrescriptionRefundFixture(String part) throws Exception {
        var expected = fixture("refund-prescription-" + part); var p = expected.path("payload");
        var receipt = fixture(part.equals("last") ? "payment-prescription-last" : "payment-prescription-first").path("payload");
        Instant at = Instant.parse(p.path("completedAt").asText()); Instant paidAt = Instant.parse(receipt.path("completedAt").asText());
        UUID accountId = id(p, "accountId"), originalId = id(p, "originalTransactionId"), request = id(receipt, "paymentRequestId");
        var account = BillingAccount.restore(accountId, id(p,"patientId"), id(p,"departmentId"), CareEpisodeType.OUTPATIENT_VISIT,
                id(p,"careEpisodeId"), AccountStatus.OPEN, "VND", 0, paidAt, null, null, paidAt, paidAt);
        var original = PaymentTransaction.restore(originalId, accountId, request, PaymentTransactionType.PAYMENT,
                PaymentClassification.SERVICE_PAYMENT, PaymentTransactionStatus.COMPLETED, receipt.path("totalAmount").decimalValue(),
                "VND", "CASH", null, "original-" + part, null, paidAt, paidAt);
        var payments = mock(LedgerPaymentRepositoryPort.class); var refunds = mock(LedgerRefundRepositoryPort.class); var events = mock(LedgerEventPort.class);
        when(payments.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(refunds.lockOriginal(originalId)).thenReturn(new LedgerRefundRepositoryPort.RefundContext(account, original,
                part.equals("remainder") ? new BigDecimal("250.00") : BigDecimal.ZERO));
        var command = new com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest("rx-refund-" + part,
                p.path("amount").decimalValue(), "Private audit must not enter event", PaymentMethod.CASH);
        new com.mediflow.billing.application.service.LedgerRefundService(payments, refunds, events,
                Mappers.getMapper(LedgerPaymentMapper.class), Clock.fixed(at, ZoneOffset.UTC))
                .refund(originalId, command, UUID.randomUUID(), expected.path("correlationId").asText());
        var capture = ArgumentCaptor.forClass(LedgerIntegrationEvent.class); verify(events).appendHeld(eq(accountId), capture.capture());
        var actual = (ObjectNode)mapper.valueToTree(capture.getValue()); actual.set("eventId", expected.get("eventId"));
        ((ObjectNode)actual.path("payload")).set("refundTransactionId", p.get("refundTransactionId"));
        assertThat(actual).isEqualTo(expected);
        verify(refunds).reverseAllocations(eq(original), any());
    }
    private JsonNode fixture(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/contracts/ledger-v1/" + name + ".json")) {
            assertThat(stream).isNotNull(); return mapper.readTree(stream);
        }
    }
    private static UUID id(JsonNode node, String field) { return UUID.fromString(node.path(field).asText()); }
}
