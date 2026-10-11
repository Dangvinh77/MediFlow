package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.application.dto.command.CaptureSurgeryCompletionCommand;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.application.service.SurgeryChargeService;
import com.mediflow.billing.application.service.SurgeryCompletionProofService;
import com.mediflow.billing.application.service.SurgeryPlannedRequestService;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.infrastructure.messaging.SurgeryChargeDecoder;
import com.mediflow.billing.infrastructure.messaging.SurgeryCompletionDecoder;
import com.mediflow.billing.infrastructure.persistence.adapter.SurgeryChargeRepositoryAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.SurgeryCompletionStoreAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.SurgeryPlannedRequestRepositoryAdapter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class SurgeryCompletionPostgresTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    private static DriverManagerDataSource source;
    private static JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private final SurgeryCompletionDecoder decoder=new SurgeryCompletionDecoder(mapper);
    private final PriceCatalogPort prices=(code,at)->{
        if(!java.util.Set.of("PRICE","EXTRA_PRICE").contains(code))throw new BillingRuleException("BILLING_PRICE_UNKNOWN","Unknown price code");
        return new PriceCatalogPort.PriceSnapshot(code,new BigDecimal("100.00"));
    };
    @BeforeAll static void migrate(){source=new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword());jdbc=new JdbcTemplate(source);Flyway.configure().dataSource(source).load().migrate();}
    @BeforeEach void setup()throws Exception {
        jdbc.execute("TRUNCATE billing_account,processed_event CASCADE");
        var planned=new SurgeryChargeDecoder(mapper).decode("surgery.case.created",Files.readAllBytes(Path.of(
                "../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.case.created.admission.v1.json")));
        tx().executeWithoutResult(status->new SurgeryPlannedRequestService(new SurgeryPlannedRequestRepositoryAdapter(jdbc),prices,
                (id,event)->{},Clock.systemUTC()).issue(planned));
    }
    private TransactionTemplate tx(){var tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setTimeout(5);return tx;}
    private ProcessedEventPort events(){return new ProcessedEventPort(){
        @Override public boolean alreadyProcessed(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM processed_event WHERE event_id=?)",Boolean.class,id));}
        @Override public void markProcessed(UUID id,String key){jdbc.update("INSERT INTO processed_event(event_id,routing_key) VALUES (?,?)",id,key);}
    };}
    private void capture(byte[] bytes) {
        var command=decoder.decode("surgery.completed",bytes);
        tx().executeWithoutResult(status->new SurgeryCompletionProofService(new SurgeryCompletionStoreAdapter(jdbc),
                new SurgeryChargeService(events(),new SurgeryChargeRepositoryAdapter(jdbc),prices,(id,event)->{}),Clock.systemUTC()).capture(command));
    }
    private byte[] fixture()throws Exception{return SurgeryCompletionDecoderTest.fixture("admission");}
    private byte[] changed(String field,String value)throws Exception {
        var root=(ObjectNode)mapper.readTree(fixture()); ((ObjectNode)root.path("payload")).put(field,value);root.put("eventId",UUID.randomUUID().toString());return mapper.writeValueAsBytes(root);
    }
    @Test void originalProofAndNewDeliveryReplayKeepOneReconciledSourceWithoutClinicalText()throws Exception {
        capture(fixture());var root=(ObjectNode)mapper.readTree(fixture());root.put("eventId",UUID.randomUUID().toString());capture(mapper.writeValueAsBytes(root));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_completion_source",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_completion_delivery",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM charge WHERE reconciled_result_id IS NOT NULL",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT completed_at_iso FROM surgery_completion_source",String.class)).isEqualTo("2026-10-07T01:10:30Z");
        assertThat(jdbc.queryForObject("SELECT row_to_json(s)::text FROM surgery_completion_source s",String.class)).doesNotContain("complications","procedureCode","payload");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement",Integer.class)).isZero();
    }
    @Test void changedImmutableCompletionCannotRepriceOrReplaceExistingProof()throws Exception {
        capture(fixture());assertThatThrownBy(()->capture(changed("completedAt","2026-10-07T01:10:31Z"))).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_completion_delivery",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT gross_amount FROM charge",BigDecimal.class)).isEqualByComparingTo("100");
    }
    @Test void actualPerformedDifferencePinsProofButDoesNotRewritePaidRequestOrInventSettlement()throws Exception {
        capture(Files.readAllBytes(Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.completed.admission.planned-difference.v1.json")));
        assertThat(jdbc.queryForObject("SELECT sum(gross_amount) FROM charge",BigDecimal.class)).isEqualByComparingTo("250");
        assertThat(jdbc.queryForObject("SELECT requested_amount FROM payment_request",BigDecimal.class)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_completion_source",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement",Integer.class)).isZero();
    }
    @Test void actualUnknownPriceIsRejectedWithoutPartialSourceOrPerformedLines()throws Exception {
        assertThatThrownBy(()->capture(Files.readAllBytes(Path.of("../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.completed.admission.unknown-price.v1.json"))))
                .isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_completion_source",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM charge WHERE reconciled_result_id IS NOT NULL",Integer.class)).isZero();
    }
    @Test void wrongPatientEpisodeOrDepartmentRollsBackAllEffects()throws Exception {
        for(var field:java.util.List.of("patientId","departmentId","surgeryRequestId"))
            assertThatThrownBy(()->capture(changed(field,UUID.randomUUID().toString()))).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_completion_source",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event",Integer.class)).isZero();
    }
    @Test void sourceWriteFailureRollsBackPerformedChargeAndProcessedReceipt()throws Exception {
        jdbc.execute("ALTER TABLE surgery_completion_source ADD CONSTRAINT test_fault CHECK (case_revision<0)");
        try{assertThatThrownBy(()->capture(fixture())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
        finally{jdbc.execute("ALTER TABLE surgery_completion_source DROP CONSTRAINT test_fault");}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM charge WHERE reconciled_result_id IS NOT NULL",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event",Integer.class)).isZero();
        capture(fixture());assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_completion_source",Integer.class)).isOne();
    }
    @Test void sourceProofReplayAfterChargeCloseDoesNotConsultCurrentPrice()throws Exception {
        capture(fixture());jdbc.update("UPDATE billing_account SET status='CHARGE_CLOSED',charge_closed_at=now()");
        var command=decoder.decode("surgery.completed",fixture());
        tx().executeWithoutResult(status->new SurgeryCompletionProofService(new SurgeryCompletionStoreAdapter(jdbc),
                new SurgeryChargeService(events(),new SurgeryChargeRepositoryAdapter(jdbc),(code,at)->{throw new IllegalStateException("price should not be read");},(id,event)->{}),Clock.systemUTC()).capture(command));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_completion_delivery",Integer.class)).isOne();
    }
    @Test void noCanonicalHistoricalProofMeansNoRetroactiveUpgradeAfterClose()throws Exception {
        var command=decoder.decode("surgery.completed",fixture());
        tx().executeWithoutResult(status->new SurgeryChargeService(events(),new SurgeryChargeRepositoryAdapter(jdbc),prices,(id,event)->{}).onSurgeryCompleted(command.event()));
        assertThatThrownBy(()->capture(fixture())).isInstanceOf(BillingRuleException.class);
        var root=(ObjectNode)mapper.readTree(fixture());root.put("eventId",UUID.randomUUID().toString());
        assertThatThrownBy(()->capture(mapper.writeValueAsBytes(root))).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_completion_source",Integer.class)).isZero();
    }
    @Test void forgedDatabaseTimeCannotSurviveMatchedSourceHash()throws Exception {
        capture(fixture());jdbc.update("UPDATE surgery_completion_source SET completed_at_iso='2026-10-07T01:10:31Z'");
        assertThatThrownBy(()->capture(fixture())).isInstanceOf(BillingRuleException.class);
    }
    @Test void deliveryIdReuseWithChangedMetadataRejectsWithoutSecondEffect()throws Exception {
        capture(fixture());var root=(ObjectNode)mapper.readTree(fixture());root.put("correlationId","changed");
        assertThatThrownBy(()->capture(mapper.writeValueAsBytes(root))).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_completion_delivery",Integer.class)).isOne();
    }
    @Test void concurrentReplicasPersistOneSourceAndTwoDistinctDeliveries()throws Exception {
        byte[] first=fixture(); var root=(ObjectNode)mapper.readTree(first);root.put("eventId",UUID.randomUUID().toString());byte[] second=mapper.writeValueAsBytes(root);
        try(var pool=Executors.newFixedThreadPool(2)){var go=new CountDownLatch(1);
            var one=pool.submit(()->{if(!go.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");capture(first);return true;});
            var two=pool.submit(()->{if(!go.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");capture(second);return true;});go.countDown();
            assertThat(one.get(15,TimeUnit.SECONDS)).isTrue();assertThat(two.get(15,TimeUnit.SECONDS)).isTrue();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_completion_source",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM surgery_completion_delivery",Integer.class)).isEqualTo(2);
    }
}
