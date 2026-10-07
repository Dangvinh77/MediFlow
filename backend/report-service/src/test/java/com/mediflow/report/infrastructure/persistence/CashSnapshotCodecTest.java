package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.mapper.BillingCashReceiptMapper;
import com.mediflow.report.domain.model.CashReceipt;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import com.mediflow.report.infrastructure.persistence.adapter.CashSnapshotCodec;

class CashSnapshotCodecTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CashSnapshotCodec codec = new CashSnapshotCodec(mapper);

    @ParameterizedTest @ValueSource(strings = {"service", "deposit"})
    void snapshot_actualReceipt_roundTripsExactlyWithoutInventingProducerEnvelope(String type) throws Exception {
        var fact = receipt(type);
        String json = codec.snapshot(fact);
        assertThat(codec.decode(fact.transactionId(), json, codec.fingerprint(fact), 1, 1)).isEqualTo(fact);
        assertThat(json).doesNotContain("eventType", "correlationId", "diagnosis", "sourceRevision");
        assertThat(mapper.readTree(json).size()).isEqualTo(15);
    }

    @Test
    void snapshot_nullInvoiceAndLargeExactCents_surviveJsonAndHash() {
        var fact = new CashReceipt(UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "ADMISSION", UUID.randomUUID(),
                CashReceipt.Classification.ADMISSION_DEPOSIT, new BigDecimal("99999999999999999.99"),
                "VND", "TRANSFER", java.time.Instant.parse("2026-12-31T18:30:00.123456789Z"),
                java.time.LocalDate.of(2027, 1, 1), "Asia/Bangkok");
        assertThat(codec.decode(fact.transactionId(), codec.snapshot(fact), codec.fingerprint(fact), 1, 1))
                .isEqualTo(fact);
    }

    @Test
    void fingerprint_differentNumericScaleAndMapOrder_preservesV12CanonicalBytes() {
        var one = new LinkedHashMap<String, Object>();
        one.put("z", new BigDecimal("100.00")); one.put("a", 1);
        var two = new LinkedHashMap<String, Object>();
        two.put("a", new BigDecimal("1.000")); two.put("z", 100);
        assertThat(codec.json(one)).isEqualTo("{\"a\":1,\"z\":100}");
        assertThat(codec.evidenceFingerprint(one)).isEqualTo(codec.evidenceFingerprint(two));
    }

    @ParameterizedTest @ValueSource(strings = {"invoiceId", "amount", "completedAt", "reportZone", "patientId"})
    void decode_missingField_isRejectedEvenWhenOptionalInvoiceWouldBeNull(String field) throws Exception {
        var fact = receipt("service");
        var fields = new LinkedHashMap<>(codec.factFields(fact)); fields.remove(field);
        assertThatThrownBy(() -> codec.decode(fact.transactionId(), codec.json(fields), codec.fingerprint(fact), 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_extraField_malformedJson_andWrongIdentity_areRejected() throws Exception {
        var fact = receipt("service");
        var fields = new LinkedHashMap<>(codec.factFields(fact)); fields.put("diagnosis", "must not be retained");
        assertThatThrownBy(() -> codec.decode(fact.transactionId(), codec.json(fields), codec.fingerprint(fact), 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(fact.transactionId(), "{", codec.fingerprint(fact), 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(UUID.randomUUID(), codec.snapshot(fact), codec.fingerprint(fact), 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_unknownSnapshotOrProjectorVersion_doesNotDowngrade() throws Exception {
        var fact = receipt("service");
        assertThatThrownBy(() -> codec.decode(fact.transactionId(), codec.snapshot(fact), codec.fingerprint(fact), 2, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode(fact.transactionId(), codec.snapshot(fact), codec.fingerprint(fact), 1, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_amountTimeOrDepartmentChangedWithoutHashChange_conflicts() throws Exception {
        var fact = receipt("service");
        for (var change : java.util.Map.of("amount", new BigDecimal("100.01"), "departmentId", UUID.randomUUID().toString(),
                "completedAt", "2026-10-05T08:00:00.000000001Z").entrySet()) {
            var fields = new LinkedHashMap<>(codec.factFields(fact)); fields.put(change.getKey(), change.getValue());
            assertThatThrownBy(() -> codec.decode(fact.transactionId(), codec.json(fields), codec.fingerprint(fact), 1, 1))
                    .hasMessageContaining("fingerprint");
        }
    }

    private CashReceipt receipt(String type) throws Exception {
        var event = new CareFinanceEnvelopeDecoder(mapper).decode("payment.completed", Files.readAllBytes(Path.of(
                "../billing-service/src/test/resources/contracts/ledger-v1/payment-" + type + ".json")));
        return new BillingCashReceiptMapper(ZoneId.of("Asia/Bangkok")).map(event);
    }
}
