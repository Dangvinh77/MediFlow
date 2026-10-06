package com.mediflow.billing.application.event;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.io.InputStream;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.port.out.*;
import com.mediflow.billing.application.service.LedgerPaymentService;
import com.mediflow.billing.domain.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;

/** Checks actual producer service output; consumers read these same files without copying them. */
class LedgerProducerFixtureTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @ParameterizedTest @ValueSource(strings = {"prescription", "exam", "lab", "admission", "surgery"})
    void clearanceFixtureMatchesActualProducerPayload(String name) throws Exception {
        var fixture = fixture("clearance-" + name);
        var payload = mapper.treeToValue(fixture.path("payload"), LedgerIntegrationEvent.ClearanceGrantedPayload.class);
        var account = BillingAccount.restore(payload.accountId(), payload.patientId(), id(8), CareEpisodeType.valueOf(payload.careEpisodeType()),
                payload.careEpisodeId(), AccountStatus.OPEN, "VND", 0, now(), null, null, now(), now());
        var request = PaymentRequest.restore(id(12), payload.invoiceId(), account.getAccountId(), PaymentRequestPurpose.valueOf(payload.purpose()),
                PaymentRequestStatus.PENDING, payload.amount(), "VND", null, id(9), now(), null);
        var target = new ClearanceTarget(payload.appointmentId(), payload.recordId(), payload.labTestIds(), payload.prescriptionId(),
                payload.admissionId(), payload.surgeryCaseId());
        var repository = mock(LedgerPaymentRepositoryPort.class);
        var events = mock(LedgerEventPort.class);
        when(repository.lockRequest(id(12))).thenReturn(new LedgerPaymentRepositoryPort.PaymentContext(account, request, target, BigDecimal.ZERO));
        when(repository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.saveClearance(any())).thenAnswer(call -> {
            FinancialClearance grant = call.getArgument(0);
            return FinancialClearance.restore(payload.clearanceId(), grant.getAccountId(), grant.getPaymentRequestId(), grant.getInvoiceId(),
                    grant.getPatientId(), grant.getPurpose(), grant.getCareEpisodeType(), grant.getCareEpisodeId(), grant.getAppointmentId(),
                    grant.getRecordId(), grant.getLabTestIds(), grant.getPrescriptionId(), grant.getAdmissionId(), grant.getSurgeryCaseId(),
                    grant.getAmount(), grant.getCurrency(), grant.getPaymentMethod(), false, grant.getExpiresAt(), grant.getGrantedAt(), null);
        });
        new LedgerPaymentService(repository, events, Mappers.getMapper(LedgerPaymentMapper.class), Clock.fixed(now(), ZoneOffset.UTC))
                .complete(id(12), new CompleteLedgerPaymentRequest("fixture", payload.amount(), "VND", PaymentMethod.CASH, null), id(9), id(9).toString());
        var capture = ArgumentCaptor.forClass(LedgerIntegrationEvent.class);
        verify(events, times(2)).appendHeld(eq(account.getAccountId()), capture.capture());
        var emitted = capture.getAllValues().getLast();
        assertThat(mapper.<JsonNode>valueToTree(emitted.payload())).isEqualTo(fixture.path("payload"));
        assertThat(emitted.eventType()).isEqualTo(fixture.path("eventType").asText());
        assertThat(emitted.version()).isEqualTo(1);
        assertThat(emitted.producer()).isEqualTo("billing-service");
        assertThat(emitted.correlationId()).isEqualTo(fixture.path("correlationId").asText());

        if (name.equals("prescription") || name.equals("admission")) {
            var receiptFixture = fixture("payment-" + (name.equals("admission") ? "deposit" : "service"));
            var actual = mapper.<JsonNode>valueToTree(capture.getAllValues().getFirst().payload());
            // Only transaction identity is generated at runtime; all business fields must match.
            ((com.fasterxml.jackson.databind.node.ObjectNode) actual).set("transactionId", receiptFixture.path("payload").path("transactionId"));
            assertThat(actual).isEqualTo(receiptFixture.path("payload"));
        }
    }

    private JsonNode fixture(String name) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/contracts/ledger-v1/" + name + ".json")) {
            assertThat(stream).isNotNull();
            return mapper.readTree(stream);
        }
    }
    private static UUID id(int suffix) { return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix)); }
    private static Instant now() { return Instant.parse("2026-10-05T08:00:00Z"); }
}
