package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.application.dto.command.SurgeryChargeCommand;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.SurgeryPlannedRequestRepositoryPort;
import com.mediflow.billing.application.service.SurgeryPlannedRequestService;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.infrastructure.messaging.SurgeryChargeDecoder;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurgeryChargeContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SurgeryChargeDecoder decoder = new SurgeryChargeDecoder(mapper);
    @ParameterizedTest @ValueSource(strings = {"admission", "outpatient"})
    void decode_actualProducerBytes_exactSourceAndEpisode(String context) throws Exception {
        var command = decoder.decode("surgery.case.created", fixture(context));
        assertThat(command.plannedItems()).containsExactly(new SurgeryChargeCommand.Item("ITEM", "PRICE", BigDecimal.ONE));
        assertThat(command.careEpisodeType()).isEqualTo(context.equals("admission") ? "ADMISSION" : "OUTPATIENT_VISIT");
        assertThat(command.sourceFingerprint()).hasSize(64);
    }
    @ParameterizedTest @ValueSource(strings = {"version", "producer", "sourceId", "sourceRevision", "caseRevision", "admissionId", "quantity", "itemCode", "occurredAt", "priority"})
    void decode_malformedContext_rejectsWithoutPayloadCause(String field) throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("admission")); var payload = (ObjectNode) root.get("payload");
        switch (field) {
            case "version" -> root.put("version", 2);
            case "producer" -> root.put("producer", "clinical-service");
            case "occurredAt" -> root.put("occurredAt", "2020-01-01T00:00:00Z");
            case "sourceRevision" -> payload.put(field, 2);
            case "caseRevision" -> payload.put(field, 1);
            case "quantity" -> ((ObjectNode) payload.path("plannedItems").get(0)).put("quantity", "1");
            case "itemCode" -> ((ObjectNode) payload.path("plannedItems").get(0)).put(field, "invalid space");
            case "priority" -> payload.put(field, "UNKNOWN");
            default -> payload.put(field, UUID.randomUUID().toString());
        }
        assertThatThrownBy(() -> decoder.decode("surgery.case.created", mapper.writeValueAsBytes(root)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid Surgery charge contract").hasNoCause();
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.00001", "1000000000000000"})
    void decode_quantityStorageBoundary_isNeverRounded(String quantity) throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("admission"));
        ((ObjectNode) root.path("payload").path("plannedItems").get(0)).put("quantity", new BigDecimal(quantity));
        assertThatThrownBy(() -> decoder.decode("surgery.case.created", mapper.writeValueAsBytes(root))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void decode_duplicateKeysAndTrailingBody_reject() throws Exception {
        String body = new String(fixture("admission"), java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> decoder.decode("surgery.case.created", body.replace("\"version\": 1", "\"version\": 1, \"version\": 1").getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> decoder.decode("surgery.case.created", (body + " {}").getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void issue_recordedSource_replaysWithoutCatalogOrStateMutation() throws Exception {
        var repository = mock(SurgeryPlannedRequestRepositoryPort.class); var prices = mock(PriceCatalogPort.class); var events = mock(LedgerEventPort.class);
        var command = decoder.decode("surgery.case.created", fixture("admission")); UUID previous = UUID.randomUUID();
        when(repository.lockRecordedSource(command)).thenReturn(Optional.of(previous));
        assertThat(service(repository, prices, events).issue(command)).isEqualTo(previous);
        verify(repository).claimDelivery(command); verifyNoInteractions(prices, events); verify(repository, never()).openAndLockExactAccount(any());
    }
    @ParameterizedTest @ValueSource(strings = {"-1", "1.001", "100000000000000000", "0"})
    void issue_invalidPriceOrZeroTotal_noRequestOrOutbox(String amount) throws Exception {
        var repository = mock(SurgeryPlannedRequestRepositoryPort.class); var prices = mock(PriceCatalogPort.class); var events = mock(LedgerEventPort.class);
        var command = decoder.decode("surgery.case.created", fixture("admission"));
        when(repository.lockRecordedSource(command)).thenReturn(Optional.empty()); when(repository.openAndLockExactAccount(command)).thenReturn(UUID.randomUUID());
        when(prices.requireActive("PRICE", command.requestedAt())).thenReturn(new PriceCatalogPort.PriceSnapshot("Test price", new BigDecimal(amount)));
        assertThatThrownBy(() -> service(repository, prices, events).issue(command)).isInstanceOf(BillingRuleException.class);
        verify(repository, never()).saveChargesAndRequest(any(), any(), any()); verifyNoInteractions(events);
    }
    @Test void issue_unknownCatalogCode_hasNoFallback() throws Exception {
        var repository = mock(SurgeryPlannedRequestRepositoryPort.class); var prices = mock(PriceCatalogPort.class); var events = mock(LedgerEventPort.class);
        var command = decoder.decode("surgery.case.created", fixture("admission"));
        when(repository.lockRecordedSource(command)).thenReturn(Optional.empty()); when(repository.openAndLockExactAccount(command)).thenReturn(UUID.randomUUID());
        when(prices.requireActive("PRICE", command.requestedAt())).thenThrow(new BillingRuleException("BILLING_PRICE_CODE_UNKNOWN", "BILLING_PRICE_CODE_UNKNOWN"));
        assertThatThrownBy(() -> service(repository, prices, events).issue(command)).isInstanceOf(BillingRuleException.class);
        verify(repository, never()).saveChargesAndRequest(any(), any(), any()); verifyNoInteractions(events);
    }
    @Test void issue_twoItemsSharingPrice_preservesCombinedExactQuantityAndHeldFact() throws Exception {
        var repository = mock(SurgeryPlannedRequestRepositoryPort.class); var prices = mock(PriceCatalogPort.class); var events = mock(LedgerEventPort.class);
        var original = decoder.decode("surgery.case.created", fixture("admission"));
        var command = withItems(original, List.of(new SurgeryChargeCommand.Item("A", "PRICE", new BigDecimal("1.0001")), new SurgeryChargeCommand.Item("B", "PRICE", new BigDecimal("0.5"))));
        UUID account = UUID.randomUUID(), request = UUID.randomUUID(), invoice = UUID.randomUUID();
        when(repository.lockRecordedSource(command)).thenReturn(Optional.empty()); when(repository.openAndLockExactAccount(command)).thenReturn(account);
        when(prices.requireActive("PRICE", command.requestedAt())).thenReturn(new PriceCatalogPort.PriceSnapshot("Test price", new BigDecimal("100")));
        when(repository.saveChargesAndRequest(eq(command), eq(account), any())).thenAnswer(call -> {
            List<com.mediflow.billing.domain.model.Charge> charges = call.getArgument(2);
            assertThat(charges).hasSize(1); assertThat(charges.getFirst().getQuantity()).isEqualByComparingTo("1.5001");
            assertThat(charges.getFirst().getGrossAmount()).isEqualByComparingTo("150.01");
            return new SurgeryPlannedRequestRepositoryPort.IssuedRequest(request, invoice, new BigDecimal("150.01"));
        });
        assertThat(service(repository, prices, events).issue(command)).isEqualTo(request);
        verify(events).appendHeld(eq(account), argThat(event -> event.version() == 1 && event.eventType().equals("invoice.created")));
    }
    @ParameterizedTest @ValueSource(strings = {"admission", "outpatient"})
    void invoiceFixture_matchesActualIssuerPayload(String context) throws Exception {
        var json = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        var fixture = json.readTree(getClass().getResourceAsStream("/contracts/ledger-v1/invoice-surgery-" + context + ".json"));
        var repository = mock(SurgeryPlannedRequestRepositoryPort.class); var prices = mock(PriceCatalogPort.class); var events = mock(LedgerEventPort.class);
        var command = decoder.decode("surgery.case.created", fixture(context));
        UUID account = UUID.fromString(fixture.path("payload").path("accountId").asText());
        when(repository.lockRecordedSource(command)).thenReturn(Optional.empty()); when(repository.openAndLockExactAccount(command)).thenReturn(account);
        when(prices.requireActive("PRICE", command.requestedAt())).thenReturn(new PriceCatalogPort.PriceSnapshot("Synthetic test catalog", new BigDecimal("100.00")));
        when(repository.saveChargesAndRequest(eq(command), eq(account), any())).thenReturn(new SurgeryPlannedRequestRepositoryPort.IssuedRequest(
                UUID.fromString(fixture.path("payload").path("paymentRequestId").asText()), UUID.fromString(fixture.path("payload").path("invoiceId").asText()), new BigDecimal("100.00")));
        service(repository, prices, events).issue(command);
        var capture = org.mockito.ArgumentCaptor.forClass(com.mediflow.billing.application.event.LedgerIntegrationEvent.class);
        verify(events).appendHeld(eq(account), capture.capture());
        var actual = json.<ObjectNode>valueToTree(capture.getValue()); actual.set("eventId", fixture.path("eventId"));
        assertThat(actual).isEqualTo(fixture);
    }
    private SurgeryPlannedRequestService service(SurgeryPlannedRequestRepositoryPort repository, PriceCatalogPort prices, LedgerEventPort events) {
        return new SurgeryPlannedRequestService(repository, prices, events, Clock.fixed(Instant.parse("2026-10-08T03:00:00Z"), ZoneOffset.UTC));
    }
    static SurgeryChargeCommand withItems(SurgeryChargeCommand c, List<SurgeryChargeCommand.Item> items) {
        return new SurgeryChargeCommand(c.eventId(), c.deliveryFingerprint(), c.sourceFingerprint(), c.correlationId(), c.surgeryCaseId(), c.surgeryRequestId(), c.patientId(), c.departmentId(), c.careEpisodeType(), c.careEpisodeId(), c.admissionId(), c.recordId(), c.requestedBy(), c.requestedAt(), items);
    }
    static byte[] fixture(String context) throws Exception {
        Path path = Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.case.created." + context + ".v1.json");
        if (!Files.exists(path)) path = Path.of("backend/surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.case.created." + context + ".v1.json");
        return Files.readAllBytes(path);
    }
}
