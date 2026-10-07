package com.mediflow.report.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.domain.model.CashReceipt.Classification;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;

class BillingCashReceiptMapperTest {
    private final CareFinanceEnvelopeDecoder decoder = new CareFinanceEnvelopeDecoder(new ObjectMapper());
    private final BillingCashReceiptMapper mapper = new BillingCashReceiptMapper(ZoneId.of("Asia/Bangkok"));

    @ParameterizedTest
    @ValueSource(strings = {"service", "deposit"})
    void map_actualBillingProducerBytes_preservesReceiptIdentityAndClassification(String type) throws Exception {
        var event = fixture(type);
        var receipt = mapper.map(event);
        assertThat(receipt.transactionId()).isEqualTo(event.metadata().sourceId());
        assertThat(receipt.transactionId()).isNotEqualTo(receipt.invoiceId());
        assertThat(receipt.classification()).isEqualTo(type.equals("deposit")
                ? Classification.ADMISSION_DEPOSIT : Classification.SERVICE_PAYMENT);
        assertThat(receipt.amount()).isEqualByComparingTo("100.00");
        assertThat(receipt.currency()).isEqualTo("VND");
        assertThat(receipt.completedAt()).isEqualTo(Instant.parse("2026-10-05T08:00:00Z"));
        assertThat(receipt.businessDate()).isEqualTo(LocalDate.of(2026, 10, 5));
    }

    @Test
    void decode_largeDecimal_preservesCentsBeforeTreeOrMapConversion() throws Exception {
        String json = Files.readString(path("service")).replace("100.00", "99999999999999999.99");
        var event = decoder.decode("payment.completed", json.getBytes(StandardCharsets.UTF_8));
        assertThat(event.payload().get("totalAmount")).isInstanceOf(BigDecimal.class);
        assertThat(mapper.map(event).amount()).isEqualByComparingTo("99999999999999999.99");
    }

    @ParameterizedTest
    @ValueSource(strings = {"transactionId", "paymentRequestId", "accountId", "patientId", "departmentId",
            "careEpisodeType", "careEpisodeId", "classification", "totalAmount", "currency", "paymentMethod", "completedAt"})
    void map_missingSourceField_rejectsWithoutFallback(String field) throws Exception {
        var event = fixture("service");
        var payload = new LinkedHashMap<>(event.payload());
        payload.remove(field);
        assertThatThrownBy(() -> mapper.map(new DecodedCareFinanceEvent(event.metadata(), payload)))
                .isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "", "SETTLEMENT_PAYMENT", "service_payment"})
    void map_unsupportedClassification_neverDefaultsToServicePayment(String value) throws Exception {
        assertThatThrownBy(() -> mapper.map(changed(fixture("service"), "classification", value)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001", "100000000000000000.00"})
    void map_invalidMoney_neverRoundsOrTruncates(String value) throws Exception {
        assertThatThrownBy(() -> mapper.map(changed(fixture("service"), "totalAmount", new BigDecimal(value))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void map_stringAndDoubleAmounts_areNotAcceptedFinancialInputs() throws Exception {
        var event = fixture("service");
        assertThatThrownBy(() -> mapper.map(changed(event, "totalAmount", "100.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.map(changed(event, "totalAmount", 100.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void map_depositOnOutpatientAndInsuranceCash_areRejected() throws Exception {
        var event = fixture("service");
        assertThatThrownBy(() -> mapper.map(changed(event, "classification", "ADMISSION_DEPOSIT")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> mapper.map(changed(event, "paymentMethod", "INSURANCE")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void map_republishedReceipt_usesBusinessTimeAcrossYearBoundary() throws Exception {
        var event = changed(fixture("service"), "completedAt", "2026-12-31T18:30:00.123456789Z");
        var metadata = event.metadata();
        event = new DecodedCareFinanceEvent(new CareFinanceEventMetadata(metadata.eventId(), metadata.eventType(),
                1, Instant.parse("2027-01-04T08:00:00Z"), metadata.correlationId(), metadata.producer(),
                metadata.sourceField(), metadata.sourceId()), event.payload());
        assertThat(mapper.map(event).businessDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(new BillingCashReceiptMapper(ZoneId.of("UTC")).map(event).businessDate())
                .isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void map_nullInvoice_isValidAndDoesNotInventAnInvoice() throws Exception {
        assertThat(mapper.map(changed(fixture("service"), "invoiceId", null)).invoiceId()).isNull();
    }

    @Test
    void map_correctionMarker_isRejectedRatherThanRecounted() throws Exception {
        assertThatThrownBy(() -> mapper.map(changed(fixture("service"), "sourceRevision", 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void map_envelopeBeforeCompletion_isRejected() throws Exception {
        assertThatThrownBy(() -> mapper.map(changed(fixture("service"), "completedAt", "2026-10-06T08:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private DecodedCareFinanceEvent fixture(String type) throws Exception {
        return decoder.decode("payment.completed", Files.readAllBytes(path(type)));
    }

    private static Path path(String type) {
        return Path.of("../billing-service/src/test/resources/contracts/ledger-v1/payment-" + type + ".json");
    }

    private static DecodedCareFinanceEvent changed(DecodedCareFinanceEvent event, String key, Object value) {
        var payload = new LinkedHashMap<>(event.payload());
        payload.put(key, value);
        return new DecodedCareFinanceEvent(event.metadata(), payload);
    }
}
