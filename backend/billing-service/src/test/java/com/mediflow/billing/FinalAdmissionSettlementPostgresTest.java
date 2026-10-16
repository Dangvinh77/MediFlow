package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.billing.application.dto.command.FinalizeAdmissionCommand;
import com.mediflow.billing.application.dto.request.CompleteLedgerPaymentRequest;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.FinalSettlementRecognitionPayload;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.PriceCatalogPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.application.service.*;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.infrastructure.messaging.*;
import com.mediflow.billing.infrastructure.persistence.adapter.*;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class FinalAdmissionSettlementPostgresTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    private static DriverManagerDataSource source;
    private static JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final PriceCatalogPort prices=(code,at)->new PriceCatalogPort.PriceSnapshot(code,new BigDecimal("100.00"));
    private UUID account,request,actor;
    @BeforeAll static void migrate(){source=new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword());jdbc=new JdbcTemplate(source);Flyway.configure().dataSource(source).load().migrate();}
    @BeforeEach void setup()throws Exception {
        jdbc.execute("TRUNCATE billing_account,billing_event_outbox,processed_event CASCADE");actor=UUID.randomUUID();
        var planned=new SurgeryChargeDecoder(mapper).decode("surgery.case.created",Files.readAllBytes(Path.of(
                "../surgery-service/src/test/resources/contracts/surgery-outcomes-v1/surgery.case.created.admission.v1.json")));
        request=tx(false).execute(status->new SurgeryPlannedRequestService(new SurgeryPlannedRequestRepositoryAdapter(jdbc),prices,
                new LedgerEventOutboxAdapter(jdbc,mapper),Clock.systemUTC()).issue(planned));
        account=jdbc.queryForObject("SELECT account_id FROM payment_request WHERE payment_request_id=?",UUID.class,request);
        pay("initial-cash","100");
        var completed=new SurgeryCompletionDecoder(mapper).decode("surgery.completed",SurgeryCompletionDecoderTest.fixture("admission"));
        tx(false).executeWithoutResult(status->new SurgeryCompletionProofService(new SurgeryCompletionStoreAdapter(jdbc),
                new SurgeryChargeService(processed(),new SurgeryChargeRepositoryAdapter(jdbc),prices,new HeldOnlyLedgerEventPort((id,event)->{})),Clock.systemUTC()).capture(completed));
        // Diagnostic medical scenario derived from the Inpatient schema, not claimed as unchanged
        // Inpatient bytes or a live medical-policy workflow: exact Surgery admission/patient/time.
        var root=(ObjectNode)mapper.readTree(AdmissionChargeCloseDecoderTest.fixture());var payload=(ObjectNode)root.path("payload");
        payload.put("admissionId",planned.admissionId().toString());payload.put("patientId",planned.patientId().toString());
        payload.put("approvedAt","2026-10-07T01:12:00Z");root.put("occurredAt","2026-10-07T01:12:00Z");
        var proof=new AdmissionChargeCloseDecoder(mapper).decode("discharge.medically.approved",mapper.writeValueAsBytes(root));
        tx(false).executeWithoutResult(status->new AdmissionChargeCloseService(new AdmissionChargeCloseStoreAdapter(jdbc),Clock.systemUTC()).close(proof));
    }
    private TransactionTemplate tx(boolean repeatable){var tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setTimeout(8);
        if(repeatable)tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);return tx;}
    private ProcessedEventPort processed(){return new ProcessedEventPort(){
        @Override public boolean alreadyProcessed(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM processed_event WHERE event_id=?)",Boolean.class,id));}
        @Override public void markProcessed(UUID id,String key){jdbc.update("INSERT INTO processed_event(event_id,routing_key) VALUES (?,?)",id,key);}
    };}
    private void pay(String key,String amount){tx(false).executeWithoutResult(status->new LedgerPaymentService(new LedgerPaymentRepositoryAdapter(jdbc),
            new LedgerEventOutboxAdapter(jdbc,mapper),org.mapstruct.factory.Mappers.getMapper(LedgerPaymentMapper.class),Clock.systemUTC()).complete(request,
            new CompleteLedgerPaymentRequest(key,new BigDecimal(amount),"VND",PaymentMethod.CASH,null),actor,key));}
    private FinalSettlementRecognitionPayload finalizeAccount(String key,LedgerEventPort events) {
        return tx(true).execute(status->new FinalAdmissionSettlementService(new FinalAdmissionSettlementStoreAdapter(jdbc,mapper),
                new AdmissionLedgerReadAdapter(jdbc),events).finalizeAdmission(new FinalizeAdmissionCommand(account,actor,key)));
    }
    private FinalSettlementRecognitionPayload finalizeAccount(String key){return finalizeAccount(key,new LedgerEventOutboxAdapter(jdbc,mapper));}
    private int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    @Test void actualMoneyPerformedAndMedicalProofCommitOneFinalOperationAndBothHeldFacts()throws Exception {
        var result=finalizeAccount("final");assertThat(finalizeAccount("final")).isEqualTo(result);
        assertThat(result.charges()).hasSize(1);assertThat(result.charges().getFirst().earnedAmount()).isEqualByComparingTo("100");
        assertThat(result.depositReleases()).isEmpty();assertThat(result.balance()).isEqualByComparingTo("0");
        assertThat(result.finalSettlement()).isTrue();assertThat(count("settlement")).isOne();assertThat(count("final_admission_settlement_operation")).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account",String.class)).isEqualTo("SETTLED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_event_outbox WHERE routing_key IN ('settlement.completed','settlement.recognition.completed') AND NOT publication_enabled",Integer.class)).isEqualTo(2);
        var sourceEvent=mapper.readTree(jdbc.queryForObject("SELECT payload FROM billing_event_outbox WHERE routing_key='settlement.recognition.completed'",String.class));
        assertThat(sourceEvent.path("payload").path("recognitionOperationId").textValue()).isEqualTo(result.recognitionOperationId().toString());
        assertThat(sourceEvent.path("payload").path("charges").get(0).path("performedAt").textValue()).isEqualTo("2026-10-07T01:10:30Z");
    }
    @Test void secondOutboxFaultRollsBackFirstFactFinalStateAndOperationThenRetryWorks() {
        long version=jdbc.queryForObject("SELECT version FROM billing_account",Long.class);var outbox=new LedgerEventOutboxAdapter(jdbc,mapper);
        assertThatThrownBy(()->finalizeAccount("retry",new HeldOnlyLedgerEventPort((id,event)->{if(event.eventType().equals("settlement.recognition.completed"))throw new IllegalStateException("outbox fault");outbox.appendHeld(id,event);})))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("settlement")).isZero();assertThat(count("final_admission_settlement_operation")).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM billing_account",Long.class)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account",String.class)).isEqualTo("CHARGE_CLOSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_event_outbox WHERE routing_key='settlement.completed'",Integer.class)).isZero();
        finalizeAccount("retry");assertThat(count("settlement")).isOne();
    }
    @Test void keyReplayBoundToActorAndNewKeyCannotFinalizeSameAccountAgain() {
        finalizeAccount("key");UUID originalActor=actor;actor=UUID.randomUUID();
        assertThatThrownBy(()->finalizeAccount("key")).isInstanceOf(com.mediflow.common.exception.DuplicateResourceException.class);
        actor=originalActor;assertThatThrownBy(()->finalizeAccount("new-key")).isInstanceOf(BillingRuleException.class);
        assertThat(count("settlement")).isOne();
    }
    @Test void noAcceptedPerformedSourceMeansNoEarnedOrFinalAuthority() {
        jdbc.execute("TRUNCATE surgery_completion_source CASCADE");
        assertThatThrownBy(()->finalizeAccount("unproved")).isInstanceOf(BillingRuleException.class);
        assertThat(count("settlement")).isZero();assertThat(jdbc.queryForObject("SELECT status FROM billing_account",String.class)).isEqualTo("CHARGE_CLOSED");
    }
    @Test void noMedicalApprovalMeansNoFinalAuthority() {
        jdbc.execute("TRUNCATE admission_charge_close CASCADE");
        assertThatThrownBy(()->finalizeAccount("unapproved")).isInstanceOf(BillingRuleException.class);assertThat(count("settlement")).isZero();
    }
    @Test void actualUnusedDepositMustBeReturnedBeforeFinalizationAndIsNotBorrowed() {
        UUID extra=UUID.randomUUID();jdbc.update("""
                INSERT INTO payment_transaction(transaction_id,account_id,transaction_type,classification,status,amount,currency,
                    payment_method,idempotency_key,completed_at) VALUES (?,?,'PAYMENT','ADMISSION_DEPOSIT','COMPLETED',20,'VND','CASH',?,now())
                """,extra,account,extra.toString());
        assertThatThrownBy(()->finalizeAccount("unused")).hasMessage("BILLING_FINAL_SETTLEMENT_MONEY_UNRESOLVED");
        assertThat(count("settlement")).isZero();
        tx(false).executeWithoutResult(status->new LedgerRefundService(new LedgerPaymentRepositoryAdapter(jdbc),new LedgerRefundRepositoryAdapter(jdbc),
                new LedgerEventOutboxAdapter(jdbc,mapper),org.mapstruct.factory.Mappers.getMapper(LedgerPaymentMapper.class),Clock.systemUTC()).refund(extra,
                new com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest("unused-return",new BigDecimal("20"),"Unused returned",PaymentMethod.CASH),actor,"unused-return"));
        assertThat(finalizeAccount("unused").grossAmount()).isEqualByComparingTo("100");
    }
    @Test void fundedButStillPlannedChargeCannotBeRecognized() {
        jdbc.update("UPDATE charge SET reconciled_result_id=NULL");assertThatThrownBy(()->finalizeAccount("planned")).isInstanceOf(BillingRuleException.class);
        assertThat(count("settlement")).isZero();
    }
    @Test void foreignPatientOrPerformedDepartmentRejectsBeforeFinalEffects() {
        jdbc.update("UPDATE surgery_completion_source SET department_id=?",UUID.randomUUID());
        assertThatThrownBy(()->finalizeAccount("foreign")).isInstanceOf(BillingRuleException.class);assertThat(count("settlement")).isZero();
    }
    @Test void insuranceAttributionCannotBeGuessedFromAccountDepartment() {
        jdbc.update("""
                INSERT INTO insurance_adjustment(adjustment_id,account_id,admission_id,decision_reference,adjustment_type,amount)
                SELECT ?,account_id,care_episode_id,'approved-test','APPROVAL',10 FROM billing_account
                """,UUID.randomUUID());
        assertThatThrownBy(()->finalizeAccount("insurance")).hasMessage("BILLING_FINAL_INSURANCE_ATTRIBUTION_UNSUPPORTED");assertThat(count("settlement")).isZero();
    }
    @Test void concurrentReplicasRetryWholeSnapshotAndReturnSameImmutableFinalOperation()throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)){var go=new CountDownLatch(1);java.util.concurrent.Callable<FinalSettlementRecognitionPayload> task=()->{
            if(!go.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");
            for(int attempt=0;attempt<3;attempt++){try{return finalizeAccount("replica");}catch(org.springframework.dao.ConcurrencyFailureException retry){if(attempt==2)throw retry;}}
            throw new IllegalStateException("retry exhausted");};var one=pool.submit(task);var two=pool.submit(task);go.countDown();
            assertThat(one.get(20,TimeUnit.SECONDS)).isEqualTo(two.get(20,TimeUnit.SECONDS));}
        assertThat(count("settlement")).isOne();assertThat(count("final_admission_settlement_operation")).isOne();
    }
}
