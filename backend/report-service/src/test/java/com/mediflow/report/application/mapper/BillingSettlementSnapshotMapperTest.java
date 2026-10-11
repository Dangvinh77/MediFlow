package com.mediflow.report.application.mapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class BillingSettlementSnapshotMapperTest {
    private final CareFinanceEnvelopeDecoder decoder = new CareFinanceEnvelopeDecoder(new ObjectMapper());
    private final BillingSettlementSnapshotMapper mapper = new BillingSettlementSnapshotMapper(ZoneId.of("Asia/Bangkok"));
    private DecodedCareFinanceEvent fixture(int revision) throws Exception {
        return decoder.decode("settlement.reconciled", Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/settlement-reconciled-"+revision+".json")));
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4})
    void map_sameActualBillingBytes_preservesVersionAndExactBalance(int revision) throws Exception {
        var snapshot = mapper.map(fixture(revision));
        assertThat(snapshot.settlementVersion()).isEqualTo(revision);
        assertThat(snapshot.receivable()).isEqualByComparingTo(revision==1?"40":revision==3?"15":"0");
        assertThat(snapshot.reconciledAt().getNano()).isEqualTo(123456789);
        assertThat(snapshot.finalSettlement()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"balance","grossAmount","insuranceAmount","patientLiability","completedPayments","completedRefunds","completedReversals","netAllocatedPayments"})
    void map_floatingMoney_neverSilentlyRounds(String key) throws Exception {
        var event=fixture(1); var payload=new LinkedHashMap<>(event.payload()); payload.put(key,40.0);
        assertThatThrownBy(() -> mapper.map(new DecodedCareFinanceEvent(event.metadata(),payload))).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"settlementVersion","balance","finalSettlement","supersedesSettlementId","reconciledAt","departmentId","estimatedOutcome"})
    void map_missingCanonicalField_rejectsBeforePersistence(String field) throws Exception {
        var event=fixture(1); var payload=new LinkedHashMap<>(event.payload()); payload.remove(field);
        assertThatThrownBy(() -> mapper.map(new DecodedCareFinanceEvent(event.metadata(),payload))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void map_claimedFinalSettlement_isNotAcceptedAsNonFinalStock() throws Exception {
        var event=fixture(1); var payload=new LinkedHashMap<>(event.payload()); payload.put("finalSettlement",true);
        assertThatThrownBy(() -> mapper.map(new DecodedCareFinanceEvent(event.metadata(),payload))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void map_inconsistentBalance_rejects() throws Exception {
        var event=fixture(1); var payload=new LinkedHashMap<>(event.payload()); payload.put("balance",new java.math.BigDecimal("41"));
        assertThatThrownBy(() -> mapper.map(new DecodedCareFinanceEvent(event.metadata(),payload))).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
    }
    @ParameterizedTest @ValueSource(strings={"-1.00","0.001","100000000000000000.00","1E100"})
    void map_invalidMoneyRangeAndPrecision_failsClosed(String amount) throws Exception {
        var event=fixture(1); var p=new LinkedHashMap<>(event.payload()); p.put("grossAmount",new java.math.BigDecimal(amount));
        assertThatThrownBy(()->mapper.map(new DecodedCareFinanceEvent(event.metadata(),p))).isInstanceOf(com.mediflow.report.domain.exception.ReportRuleException.class);
    }
    @ParameterizedTest @ValueSource(strings={"floating","string","fractional","overflow"})
    void map_nonIntegralRevision_rejects(String scenario) throws Exception {
        var event=fixture(1); var p=new LinkedHashMap<>(event.payload());
        p.put("settlementVersion",switch(scenario) {
            case "floating"->1.0; case "string"->"1"; case "fractional"->new java.math.BigDecimal("1.5"); default->Long.MAX_VALUE;
        });
        assertThatThrownBy(()->mapper.map(new DecodedCareFinanceEvent(event.metadata(),p))).isInstanceOf(IllegalArgumentException.class);
    }
}
