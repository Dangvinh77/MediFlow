package com.mediflow.report.domain.model;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.mapper.*;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;

class CashRefundTest {
    private final ZoneId zone = ZoneId.of("Asia/Bangkok");
    private final CareFinanceEnvelopeDecoder decoder = new CareFinanceEnvelopeDecoder(new ObjectMapper());
    @ParameterizedTest @ValueSource(strings = {"service", "deposit"})
    void verifyOriginal_actualProducerPair_preservesReceiptAndRefundBusinessPeriods(String context) throws Exception {
        var refund = refund(context); var receipt = receipt(context);
        refund.verifyOriginal(receipt, new BigDecimal("80"));
        assertThat(refund.businessDate()).isEqualTo("2026-10-08");
        assertThat(receipt.businessDate()).isEqualTo("2026-10-05");
        assertThatThrownBy(() -> refund.verifyOriginal(receipt, new BigDecimal("80.01"))).hasMessage("Invalid cash refund evidence");
    }
    @ParameterizedTest @ValueSource(strings = {"accountId", "patientId", "departmentId", "careEpisodeId", "originalTransactionId", "completedAt"})
    void verifyOriginal_wrongContextOrEarlierRefund_rejects(String field) throws Exception {
        var event = decoder.decode("payment.refunded", fixture("refund-service")); var p = new LinkedHashMap<>(event.payload());
        if (field.equals("completedAt")) p.put(field, "2026-10-04T04:00:00Z"); else p.put(field, UUID.randomUUID().toString());
        var m = event.metadata();
        if (field.equals("completedAt")) m = new com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata(
                m.eventId(), m.eventType(), m.version(), java.time.Instant.parse(p.get(field).toString()), m.correlationId(), m.producer(), m.sourceField(), m.sourceId());
        var refund = new BillingCashRefundMapper(zone).map(new com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent(m, p));
        assertThatThrownBy(() -> refund.verifyOriginal(receipt("service"), BigDecimal.ZERO)).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
    }
    @Test void map_decimalNumber_remainsExactAndTextAmountsRejected() throws Exception {
        var event = decoder.decode("payment.refunded", fixture("refund-service")); var p = new LinkedHashMap<>(event.payload());
        p.put("amount", new BigDecimal("12345678901234567.01"));
        assertThat(new BillingCashRefundMapper(zone).map(new com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent(event.metadata(), p)).amount())
                .isEqualByComparingTo("12345678901234567.01");
        p.put("amount", "20.00");
        assertThatThrownBy(() -> new BillingCashRefundMapper(zone).map(new com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent(event.metadata(), p)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    private CashRefund refund(String context) throws Exception { return new BillingCashRefundMapper(zone).map(decoder.decode("payment.refunded", fixture("refund-" + context))); }
    private CashReceipt receipt(String context) throws Exception { return new BillingCashReceiptMapper(zone).map(decoder.decode("payment.completed", fixture("payment-" + context))); }
    private static byte[] fixture(String name) throws Exception { return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/" + name + ".json")); }
}
