package com.mediflow.report.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.*;
import com.mediflow.report.application.service.*;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;
import com.mediflow.report.infrastructure.persistence.adapter.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@com.mediflow.report.support.ReportPersistenceTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({CashReceiptApplicationService.class,CashReceiptPersistenceAdapter.class,CashRefundApplicationService.class,CashRefundPersistenceAdapter.class,
        FinalSettlementRecognitionApplicationService.class,FinalSettlementRecognitionStoreAdapter.class,FinalSettlementRecognitionPostgresTest.Config.class})
@Testcontainers
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class FinalSettlementRecognitionPostgresTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired CashReceiptApplicationService receipts;
    @Autowired CashRefundApplicationService refunds;
    @Autowired FinalSettlementRecognitionApplicationService finals;
    @Autowired FinalSettlementRecognitionStoreAdapter store;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clean(){jdbc.execute("TRUNCATE report_final_recognition,report_final_recognition_daily,report_cash_receipt,report_gross_cash_daily,report_cash_refund,report_refund_cash_daily CASCADE");}
    @Test void unchangedActualProducerFixturesRecognizeOnlyFinal100AndReleaseOriginal100AtReceiptDepartment()throws Exception {
        cash();finals.apply(finalEvent());assertApplied();
        assertThat(amount("earned_revenue",id(9))).isEqualByComparingTo("60");assertThat(amount("earned_revenue",id(10))).isEqualByComparingTo("40");
        assertThat(amount("deposit_released",id(4))).isEqualByComparingTo("100");assertThat(amount("earned_revenue",id(4))).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT SUM(gross_receipts) FROM report_gross_cash_daily WHERE department_id IS NULL",BigDecimal.class)).isEqualByComparingTo("120");
        assertThat(jdbc.queryForObject("SELECT SUM(completed_refunds) FROM report_refund_cash_daily WHERE department_id IS NULL",BigDecimal.class)).isEqualByComparingTo("20");
        assertThat(count("financial_report_generation")).isZero();assertThat(count("financial_report_publication")).isZero();assertThat(count("daily_financial_report")).isZero();
    }
    @Test void finalAndRefundBeforeOriginalStayPendingThenRecoverAfterReceiptWithoutCashInvention()throws Exception {
        finals.apply(finalEvent());refunds.apply(refundEvent());assertThat(state()).isEqualTo("PENDING");assertThat(count("report_final_recognition_daily")).isZero();
        receipts.apply(receiptEvent());assertThat(state()).isEqualTo("PENDING");finals.recover(id(15));assertApplied();
    }
    @Test void missingActualRefundDoesNotRecognizeThoughAllocationEqualsEarned()throws Exception {
        receipts.apply(receiptEvent());finals.apply(finalEvent());assertThat(state()).isEqualTo("PENDING");
        refunds.apply(refundEvent());finals.recover(id(15));assertApplied();
    }
    @Test void exactReplayAndNewDeliveryHaveSingleSourceContributionAndRelease()throws Exception {
        cash();finals.apply(finalEvent());finals.apply(finalEvent());var root=finalRoot();root.put("eventId",UUID.randomUUID().toString());
        finals.apply(decode(root));finals.recover(id(15));assertApplied();assertThat(count("report_final_recognition_delivery")).isEqualTo(2);
        assertThat(count("report_final_recognition_contribution")).isEqualTo(4);assertThat(count("report_final_deposit_release")).isOne();
    }
    @Test void changedSourceDepartmentRejectsAndCannotRetainNewDelivery()throws Exception {
        cash();finals.apply(finalEvent());var root=finalRoot();root.put("eventId",UUID.randomUUID().toString());
        ((ObjectNode)root.get("payload").get("charges").get(0)).put("departmentId",id(41).toString());
        assertThatThrownBy(()->finals.apply(decode(root))).hasMessage("Final recognition evidence conflict");assertApplied();assertThat(count("report_final_recognition_delivery")).isOne();
    }
    @Test void reusedDeliveryWithChangedCorrelationRejectsBeforeSecondEffect()throws Exception {
        cash();finals.apply(finalEvent());var root=finalRoot();root.put("correlationId","changed");
        assertThatThrownBy(()->finals.apply(decode(root))).hasMessage("Final recognition evidence conflict");assertApplied();
    }
    @Test void secondOperationOrSettlementForSameAccountCannotDoubleRecognize()throws Exception {
        cash();finals.apply(finalEvent());var root=finalRoot();root.put("eventId",UUID.randomUUID().toString());
        ((ObjectNode)root.get("payload")).put("recognitionOperationId",id(42).toString()).put("settlementId",id(43).toString());
        assertThatThrownBy(()->finals.apply(decode(root))).hasMessage("Final recognition evidence conflict");assertThat(count("report_final_recognition")).isOne();assertApplied();
    }
    @Test void wrongPatientReceiptQuarantinesEarlySourceWithoutPartialEarnedOrRelease()throws Exception {
        finals.apply(finalEvent());var root=cashRoot("payment-final-recognition-deposit");((ObjectNode)root.get("payload")).put("patientId",id(44).toString());
        receipts.apply(cashDecode(root));finals.recover(id(15));assertThat(state()).isEqualTo("REJECTED");
        assertThat(count("report_final_recognition_daily")).isZero();assertThat(count("report_final_deposit_release")).isZero();
    }
    @Test void wrongReceiptDepartmentQuarantinesEarlySourceInsteadOfReallocatingCash() throws Exception {
        finals.apply(finalEvent());
        var payment = cashRoot("payment-final-recognition-deposit");
        ((ObjectNode)payment.get("payload")).put("departmentId",id(44).toString());
        var refund = cashRoot("refund-final-recognition-deposit");
        ((ObjectNode)refund.get("payload")).put("departmentId",id(44).toString());
        receipts.apply(cashDecode(payment)); refunds.apply(cashDecode(refund)); finals.recover(id(15));
        assertThat(state()).isEqualTo("REJECTED"); assertThat(count("report_final_deposit_release")).isZero();
        assertThat(count("report_final_recognition_daily")).isZero();
        assertThat(jdbc.queryForObject("SELECT gross_receipts FROM report_gross_cash_daily WHERE department_id=?",BigDecimal.class,id(44)))
                .isEqualByComparingTo("120");
    }
    @Test void excessLocalAccountCashRejectsInsteadOfGuessingWhichReceiptFundsSource()throws Exception {
        cash();var extra=cashRoot("payment-final-recognition-deposit");extra.put("eventId",id(45).toString());
        ((ObjectNode)extra.get("payload")).put("transactionId",id(46).toString());receipts.apply(cashDecode(extra));
        assertThatThrownBy(()->finals.apply(finalEvent())).hasMessage("Invalid final-only recognition evidence");
        assertThat(count("report_final_recognition")).isZero();assertThat(count("report_final_recognition_daily")).isZero();
    }
    @Test void failureInDepartmentWriteRollsBackDeliveryStateReleaseAndHospitalContribution()throws Exception {
        cash();jdbc.execute("CREATE FUNCTION test_final_write_fault() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced contribution fault'; END; $$");
        jdbc.execute("CREATE TRIGGER test_final_write_fault BEFORE INSERT ON report_final_recognition_daily FOR EACH ROW EXECUTE FUNCTION test_final_write_fault()");
        try {
            assertThatThrownBy(()->finals.apply(finalEvent())).isInstanceOf(org.springframework.dao.DataAccessException.class);
            for(String table:new String[]{"report_final_recognition","report_final_recognition_delivery","report_final_recognition_contribution","report_final_deposit_release","report_final_recognition_daily"})assertThat(count(table)).isZero();
        }finally{jdbc.execute("DROP TRIGGER test_final_write_fault ON report_final_recognition_daily");jdbc.execute("DROP FUNCTION test_final_write_fault()");}
        finals.apply(finalEvent());assertApplied();
    }
    @Test void hospitalScopeIsWrittenBeforeAnyDepartmentScope() throws Exception {
        cash();
        jdbc.execute("""
                CREATE FUNCTION assert_final_hospital_first() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.department_id IS NOT NULL AND NOT EXISTS (
                        SELECT 1 FROM report_final_recognition_daily WHERE business_date=NEW.business_date
                            AND currency=NEW.currency AND report_zone=NEW.report_zone AND department_id IS NULL
                    ) THEN RAISE EXCEPTION 'Hospital fence must precede departments'; END IF;
                    RETURN NEW;
                END; $$
                """);
        jdbc.execute("CREATE TRIGGER assert_final_hospital_first BEFORE INSERT ON report_final_recognition_daily FOR EACH ROW EXECUTE FUNCTION assert_final_hospital_first()");
        try { finals.apply(finalEvent()); assertApplied(); }
        finally {
            jdbc.execute("DROP TRIGGER assert_final_hospital_first ON report_final_recognition_daily");
            jdbc.execute("DROP FUNCTION assert_final_hospital_first()");
        }
    }
    @Test void completedReleasePreventsLaterRefundBorrowingAlreadyEarnedDepositBudget()throws Exception {
        cash();finals.apply(finalEvent());var root=cashRoot("refund-final-recognition-deposit");root.put("eventId",id(47).toString());
        ((ObjectNode)root.get("payload")).put("refundTransactionId",id(48).toString()).put("amount",new BigDecimal("1.00"));
        assertThatThrownBy(()->refunds.apply(cashDecode(root))).hasMessage("Invalid cash refund evidence");assertThat(count("report_cash_refund")).isOne();assertApplied();
    }
    @Test void twoReplicasRaceOnSameSourceAndHaveOneAtomicEffect()throws Exception {
        cash();var event=finalEvent();var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Void> action=()->{ready.countDown();if(!start.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");finals.apply(event);return null;};
            var one=pool.submit(action);var two=pool.submit(action);assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();start.countDown();one.get(15,TimeUnit.SECONDS);two.get(15,TimeUnit.SECONDS);
        }
        assertApplied();assertThat(count("report_final_recognition_delivery")).isOne();assertThat(count("report_final_deposit_release")).isOne();
    }
    @Test void snapshotFingerprintMutationIsQuarantinedAndNeverProjected()throws Exception {
        finals.apply(finalEvent());cash();jdbc.update("UPDATE report_final_recognition SET payload_fingerprint=?","0".repeat(64));finals.recover(id(15));
        assertThat(state()).isEqualTo("REJECTED");assertThat(count("report_final_recognition_daily")).isZero();
    }
    @Test void pendingPollIsBoundedAndDeferralDoesNotEraseOrMarkMissingCashComplete()throws Exception {
        finals.apply(finalEvent());assertThat(store.pending(1)).containsExactly(id(15));store.defer(id(15));assertThat(store.pending(20)).isEmpty();assertThat(state()).isEqualTo("PENDING");
        for(int invalid:new int[]{0,21})assertThatThrownBy(()->store.pending(invalid)).hasMessage("Final recognition evidence conflict");
    }
    @Test void reportZoneChangeCannotRewriteAcceptedBusinessDate()throws Exception {
        cash();finals.apply(finalEvent());var event=finalEvent();var other=new BillingFinalSettlementRecognitionMapper(ZoneId.of("UTC")).map(event);
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        assertThatThrownBy(()->tx.executeWithoutResult(status->store.apply(event,other))).hasMessage("Final recognition evidence conflict");assertApplied();
    }
    @Test void boundedCashAccountQueriesHaveMatchingIndexes() {
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname='public' AND indexname IN (?,?)",String.class,
                "idx_cash_receipt_account_transaction","idx_cash_refund_applied_account_transaction")).hasSize(2);
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    private void cash()throws Exception {receipts.apply(receiptEvent());refunds.apply(refundEvent());}
    private void assertApplied(){assertThat(state()).isEqualTo("APPLIED");assertThat(amount("earned_revenue",null)).isEqualByComparingTo("100");assertThat(amount("deposit_released",null)).isEqualByComparingTo("100");}
    private String state(){return jdbc.queryForObject("SELECT state FROM report_final_recognition",String.class);}
    private long count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class);}
    private BigDecimal amount(String field,UUID department){return jdbc.queryForObject("SELECT "+field+" FROM report_final_recognition_daily WHERE department_id IS NOT DISTINCT FROM ?",BigDecimal.class,department);}
    private static DecodedCareFinanceEvent finalEvent()throws Exception{return decode(finalRoot());}
    private static DecodedCareFinanceEvent receiptEvent()throws Exception{return cashDecode(cashRoot("payment-final-recognition-deposit"));}
    private static DecodedCareFinanceEvent refundEvent()throws Exception{return cashDecode(cashRoot("refund-final-recognition-deposit"));}
    private static ObjectNode finalRoot()throws Exception{return cashRoot("settlement-recognition-completed");}
    private static ObjectNode cashRoot(String name)throws Exception{return (ObjectNode)new ObjectMapper().readTree(Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/"+name+".json")));}
    private static DecodedCareFinanceEvent cashDecode(ObjectNode root)throws Exception{return new CareFinanceEnvelopeDecoder(new ObjectMapper()).decode(root.get("eventType").asText(),new ObjectMapper().writeValueAsBytes(root));}
    private static DecodedCareFinanceEvent decode(ObjectNode root)throws Exception{return cashDecode(root);}
    private static UUID id(int value){return UUID.fromString("00000000-0000-4000-8000-"+String.format("%012d",value));}
    @TestConfiguration static class Config {
        @Bean ObjectMapper mapper(){return new ObjectMapper();}
        @Bean BillingCashReceiptMapper receiptMapper(){return new BillingCashReceiptMapper(ZoneId.of("Asia/Bangkok"));}
        @Bean BillingCashRefundMapper refundMapper(){return new BillingCashRefundMapper(ZoneId.of("Asia/Bangkok"));}
        @Bean BillingFinalSettlementRecognitionMapper finalMapper(){return new BillingFinalSettlementRecognitionMapper(ZoneId.of("Asia/Bangkok"));}
    }
}
