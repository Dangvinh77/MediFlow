package com.mediflow.report.application.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class BillingFinalSettlementRecognitionMapperTest {
    private final ObjectMapper json=new ObjectMapper();
    private final CareFinanceEnvelopeDecoder decoder=new CareFinanceEnvelopeDecoder(json);
    private final BillingFinalSettlementRecognitionMapper mapper=new BillingFinalSettlementRecognitionMapper(ZoneId.of("Asia/Bangkok"));
    static byte[] fixture()throws Exception{return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/settlement-recognition-completed.json"));}
    @Test void exactActualBillingServiceBytesYieldFinalEarnedSplitAndOriginalDepositReleaseOnly()throws Exception {
        var result=mapper.map(decoder.decode("settlement.recognition.completed",fixture()));
        assertThat(result.businessDate()).isEqualTo(java.time.LocalDate.of(2026,10,10));assertThat(result.finalSettlement()).isTrue();
        assertThat(result.earnedByDepartment().values()).extracting(java.math.BigDecimal::toPlainString).containsExactlyInAnyOrder("60.00","40.00");
        assertThat(result.earnedByDepartment()).doesNotContainKey(result.accountDepartmentId());
        assertThat(result.depositReleases().getFirst().amount()).isEqualByComparingTo("100");
        assertThat(result.depositReleases().getFirst().receiptDepartmentId()).isEqualTo(result.accountDepartmentId());
        assertThat(result.totals().completedPayments()).isEqualByComparingTo("120");
        // The 120 receipt is not added to earned revenue; only explicitly recognized charges sum to 100.
        assertThat(result.totals().grossAmount()).isEqualByComparingTo("100");
    }
    @ParameterizedTest @ValueSource(strings={"nonfinal","outcome","revision","settlementRevision","supersedes","missing","extra","sourceId","completedAt",
            "balance","refunds","reversals","insurance","gross","currency","duplicateCharge","duplicateAllocation","unknownCharge","originalLink",
            "classification","allocationAmount","releaseOriginal","releaseDepartment","releaseAmount","missingRelease","performedTime","performedHash","performedType",
            "extraEnvelope","unsupportedBusinessDate"})
    void inconsistentOrUnsupportedFinancialProofNeverBecomesEarned(String scenario)throws Exception {
        var root=(ObjectNode)json.readTree(fixture());var p=(ObjectNode)root.path("payload");
        var charge=(ObjectNode)p.path("charges").get(0);var allocation=(ObjectNode)p.path("allocations").get(0);
        var release=(ObjectNode)p.path("depositReleases").get(0);
        switch(scenario) {
            case "extraEnvelope"->root.put("clinicalNarrative","must not enter financial snapshot");
            case "unsupportedBusinessDate"->{root.put("occurredAt","2101-01-01T00:01:00Z");p.put("completedAt","2101-01-01T00:01:00Z");}
            case "nonfinal"->p.put("finalSettlement",false);
            case "outcome"->p.put("outcome","DEBT_APPROVED");
            case "revision"->p.put("recognitionRevision",2);
            case "settlementRevision"->p.put("settlementVersion",2);
            case "supersedes"->p.put("supersedesOperationId",UUID.randomUUID().toString());
            case "missing"->p.remove("recognitionRevision");
            case "extra"->p.put("invoiceId",UUID.randomUUID().toString());
            case "sourceId"->p.put("recognitionOperationId","1-1-1-1-1");
            case "completedAt"->p.put("completedAt","2026-10-10T00:02:00Z");
            case "balance"->p.put("balance",1);
            case "refunds"->p.put("completedRefunds",21);
            case "reversals"->p.put("completedReversals",1);
            case "insurance"->p.put("insuranceAmount",1);
            case "gross"->p.put("grossAmount",101);
            case "currency"->p.put("currency","USD");
            case "duplicateCharge"->((com.fasterxml.jackson.databind.node.ArrayNode)p.path("charges")).add(charge.deepCopy());
            case "duplicateAllocation"->((com.fasterxml.jackson.databind.node.ArrayNode)p.path("allocations")).add(allocation.deepCopy());
            case "unknownCharge"->allocation.put("chargeId",UUID.randomUUID().toString());
            case "originalLink"->allocation.put("originalTransactionId",UUID.randomUUID().toString());
            case "classification"->allocation.put("classification","SERVICE_PAYMENT");
            case "allocationAmount"->allocation.put("amount",61);
            case "releaseOriginal"->release.put("originalTransactionId",UUID.randomUUID().toString());
            case "releaseDepartment"->release.put("receiptDepartmentId",UUID.randomUUID().toString());
            case "releaseAmount"->release.put("amount",99);
            case "missingRelease"->p.putArray("depositReleases");
            case "performedTime"->charge.put("performedAt","2026-10-10T00:02:00Z");
            case "performedHash"->charge.put("performedFingerprint","unverified");
            case "performedType"->charge.put("sourceType","EXAM");
            default->throw new IllegalArgumentException();
        }
        assertThatThrownBy(()->mapper.map(decoder.decode("settlement.recognition.completed",json.writeValueAsBytes(root)))).isInstanceOf(RuntimeException.class);
    }
    @Test void finalBusinessDateUsesConfiguredZoneNotReceiptOrArrivalDate()throws Exception {
        var root=(ObjectNode)json.readTree(fixture());root.put("occurredAt","2026-12-31T23:59:59Z");((ObjectNode)root.path("payload")).put("completedAt","2026-12-31T23:59:59Z");
        var result=mapper.map(decoder.decode("settlement.recognition.completed",json.writeValueAsBytes(root)));
        assertThat(result.businessDate()).isEqualTo(java.time.LocalDate.of(2027,1,1));
    }
    @Test void sameSourceUnderNewDeliveryMapsSameOperationNotAnotherEarnedDelta()throws Exception {
        var original=mapper.map(decoder.decode("settlement.recognition.completed",fixture()));var root=(ObjectNode)json.readTree(fixture());root.put("eventId",UUID.randomUUID().toString());
        assertThat(mapper.map(decoder.decode("settlement.recognition.completed",json.writeValueAsBytes(root)))).isEqualTo(original);
    }
}
