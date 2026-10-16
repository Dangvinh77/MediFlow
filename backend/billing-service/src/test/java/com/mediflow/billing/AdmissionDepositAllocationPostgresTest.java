package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.AllocateAdmissionDepositCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.DepositAllocatedPayload;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.service.AdmissionChargeCloseService;
import com.mediflow.billing.application.service.AdmissionDepositAllocationService;
import com.mediflow.billing.application.service.LedgerRefundService;
import com.mediflow.billing.application.dto.request.RefundLedgerPaymentRequest;
import com.mediflow.billing.application.dto.response.LedgerRefundDTO;
import com.mediflow.billing.application.mapper.LedgerPaymentMapper;
import com.mediflow.billing.domain.model.PaymentMethod;
import com.mediflow.billing.infrastructure.persistence.adapter.LedgerPaymentRepositoryAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.LedgerRefundRepositoryAdapter;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.infrastructure.messaging.AdmissionChargeCloseDecoder;
import com.mediflow.billing.infrastructure.messaging.LedgerEventOutboxAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.AdmissionChargeCloseStoreAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.AdmissionDepositAllocationStoreAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.AdmissionLedgerReadAdapter;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class AdmissionDepositAllocationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static DriverManagerDataSource source;
    private static JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private UUID account,admission,patient,first,second,original,actor;
    @BeforeAll static void migrate() {
        source=new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()); jdbc=new JdbcTemplate(source);
        Flyway.configure().dataSource(source).load().migrate();
    }
    @BeforeEach void setup() throws Exception {
        jdbc.execute("TRUNCATE admission_charge_close,billing_account,billing_event_outbox CASCADE");
        var proof=new AdmissionChargeCloseDecoder(mapper).decode("discharge.medically.approved",AdmissionChargeCloseDecoderTest.fixture());
        account=UUID.randomUUID(); admission=proof.admissionId(); patient=proof.patientId(); first=UUID.randomUUID();
        second=UUID.randomUUID(); original=UUID.randomUUID(); actor=UUID.randomUUID();
        UUID department=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO billing_account(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at)
                VALUES (?,?,?,'ADMISSION',?,'2026-09-28T02:10:00Z')
                """,account,patient,department,admission);
        for(var line:Map.of(first,new BigDecimal("48"),second,new BigDecimal("32")).entrySet()) {
            jdbc.update("""
                    INSERT INTO charge(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,
                        description,quantity,unit_amount,gross_amount,incurred_at)
                    VALUES (?,?,?,?,'PRESCRIPTION',?,'RX','fixture',1,?,?,'2026-09-28T03:00:00Z')
                    """,line.getKey(),account,patient,department,UUID.randomUUID(),line.getValue(),line.getValue());
        }
        jdbc.update("""
                INSERT INTO payment_transaction(transaction_id,account_id,transaction_type,classification,status,
                    amount,currency,payment_method,idempotency_key,completed_at)
                VALUES (?,?,'PAYMENT','ADMISSION_DEPOSIT','COMPLETED',100,'VND','CASH',?,'2026-09-28T04:00:00Z')
                """,original,account,original.toString());
        var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.executeWithoutResult(status -> new AdmissionChargeCloseService(new AdmissionChargeCloseStoreAdapter(jdbc),Clock.systemUTC()).close(proof));
    }
    private DepositAllocatedPayload run(String key, Map<UUID,BigDecimal> amounts, LedgerEventPort events) {
        var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); tx.setTimeout(5);
        return tx.execute(status -> new AdmissionDepositAllocationService(new AdmissionDepositAllocationStoreAdapter(jdbc,mapper),
                new AdmissionLedgerReadAdapter(jdbc),events).allocate(new AllocateAdmissionDepositCommand(account,original,actor,key,amounts)));
    }
    private DepositAllocatedPayload run(String key) {
        return run(key,Map.of(first,new BigDecimal("48"),second,new BigDecimal("32")),new LedgerEventOutboxAdapter(jdbc,mapper));
    }
    @Test void allocationPersistsOriginalBoundedRowsAuditAndHeldFactButNoRevenueOrFinalization() throws Exception {
        var result=run("allocation"); assertThat(run("allocation")).isEqualTo(result);
        assertThat(result.allocatedAmount()).isEqualByComparingTo("80"); assertThat(result.finalSettlement()).isFalse();
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM payment_allocation",BigDecimal.class)).isEqualByComparingTo("80");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deposit_allocation_operation",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transaction",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account",String.class)).isEqualTo("CHARGE_CLOSED");
        var body=mapper.readTree(jdbc.queryForObject("SELECT payload FROM billing_event_outbox",String.class));
        assertThat(body.path("eventType").textValue()).isEqualTo("deposit.allocated");
        assertThat(body.path("payload").path("finalSettlement").booleanValue()).isFalse();
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM billing_event_outbox",Boolean.class)).isFalse();
    }
    @Test void outboxFailureRollsBackAllocationsProofAndAccountFenceThenRetryWorks() {
        long version=jdbc.queryForObject("SELECT version FROM billing_account",Long.class);
        assertThatThrownBy(() -> run("retry",Map.of(first,new BigDecimal("48")),new HeldOnlyLedgerEventPort((id,event) -> {throw new IllegalStateException("test failure");})))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_allocation",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deposit_allocation_operation",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM billing_account",Long.class)).isEqualTo(version);
        assertThat(run("retry").allocatedAmount()).isEqualByComparingTo("80");
    }
    @Test void keyReplayIsBoundToActorAndExactIntent() {
        run("key"); actor=UUID.randomUUID();
        assertThatThrownBy(() -> run("key")).isInstanceOf(com.mediflow.common.exception.DuplicateResourceException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_event_outbox",Integer.class)).isOne();
    }
    @Test void newKeyCannotOverwriteSameOriginalChargeAllocation() {
        run("first"); assertThatThrownBy(() -> run("second")).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_allocation",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_event_outbox",Integer.class)).isOne();
    }
    @Test void exactReplayAfterFinalAccountStateIsNoEffectButNewIntentRejected() {
        var original=run("first"); jdbc.update("UPDATE billing_account SET status='SETTLED'");
        assertThat(run("first")).isEqualTo(original);
        assertThatThrownBy(() -> run("new")).isInstanceOf(BillingRuleException.class);
    }
    @Test void originalRefundBudgetCannotBeBorrowedFromAnotherPayment() {
        UUID refund=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment_transaction(transaction_id,account_id,transaction_type,classification,status,
                    amount,currency,payment_method,idempotency_key,original_transaction_id,completed_at)
                VALUES (?,?,'REFUND','ADMISSION_DEPOSIT','COMPLETED',60,'VND','CASH',?,?,'2026-09-30T10:00:00Z')
                """,refund,account,refund.toString(),original);
        assertThatThrownBy(() -> run("too-much")).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_allocation",Integer.class)).isZero();
    }
    @Test void wrongChargeAndMissingMedicalProofRejectedBeforeAllocation() {
        assertThatThrownBy(() -> run("foreign",Map.of(UUID.randomUUID(),new BigDecimal("1")),new LedgerEventOutboxAdapter(jdbc,mapper)))
                .isInstanceOf(BillingRuleException.class);
        jdbc.execute("TRUNCATE admission_charge_close CASCADE");
        assertThatThrownBy(() -> run("unverifiable")).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_allocation",Integer.class)).isZero();
    }
    @Test void concurrentReplicasRetrySerializationAndReturnOneImmutableOperation() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
            java.util.concurrent.Callable<DepositAllocatedPayload> task=() -> {
                ready.countDown(); if(!go.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                for(int retry=0;retry<3;retry++) {
                    try{return run("replica");}catch(org.springframework.dao.ConcurrencyFailureException failure){if(retry==2)throw failure;}
                }
                throw new IllegalStateException("retries exhausted");
            };
            var one=pool.submit(task); var two=pool.submit(task);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(one.get(15,TimeUnit.SECONDS)).isEqualTo(two.get(15,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deposit_allocation_operation",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_event_outbox",Integer.class)).isOne();
    }

    private LedgerRefundDTO refund(String key, String amount, LedgerEventPort events) {
        var tx=new TransactionTemplate(new DataSourceTransactionManager(source)); tx.setTimeout(5);
        return tx.execute(status -> new LedgerRefundService(new LedgerPaymentRepositoryAdapter(jdbc),
                new LedgerRefundRepositoryAdapter(jdbc),events,org.mapstruct.factory.Mappers.getMapper(LedgerPaymentMapper.class),
                Clock.systemUTC()).refund(original,new RefundLedgerPaymentRequest(key,new BigDecimal(amount),
                        "Unused admission deposit returned",PaymentMethod.CASH),actor,key));
    }
    private LedgerRefundDTO refund(String key,String amount) {
        return refund(key,amount,new LedgerEventOutboxAdapter(jdbc,mapper));
    }
    private com.mediflow.billing.domain.model.AdmissionLedgerSnapshot.Totals totals() {
        var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return tx.execute(status -> new AdmissionLedgerReadAdapter(jdbc).read(account).orElseThrow().reconcile());
    }

    @Test void deposit100Allocation80ActualRefund20KeepsFundedChargesAndZeroBalance() throws Exception {
        run("allocate"); String allocations=jdbc.queryForObject("SELECT json_agg(a ORDER BY charge_id)::text FROM payment_allocation a",String.class);
        var refunded=refund("excess","20");
        assertThat(refund("excess","20.00")).isEqualTo(refunded);
        assertThat(totals().netAllocatedPayments()).isEqualByComparingTo("80");
        assertThat(totals().unallocatedDeposit()).isEqualByComparingTo("0");
        assertThat(totals().unappliedNetPayments()).isEqualByComparingTo("0");
        assertThat(totals().balance()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT json_agg(a ORDER BY charge_id)::text FROM payment_allocation a",String.class)).isEqualTo(allocations);
        assertThat(jdbc.queryForObject("SELECT amount FROM payment_transaction WHERE transaction_id=?",BigDecimal.class,original)).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_event_outbox WHERE routing_key='payment.refunded' AND NOT publication_enabled",Integer.class)).isOne();
        var event=mapper.readTree(jdbc.queryForObject("SELECT payload FROM billing_event_outbox WHERE routing_key='payment.refunded'",String.class));
        assertThat(event.path("payload").path("originalTransactionId").textValue()).isEqualTo(original.toString());
        assertThat(event.path("payload").path("amount").decimalValue()).isEqualByComparingTo("20");
        assertThat(event.path("payload").path("reason").textValue()).isEqualTo(LedgerRefundService.PUBLIC_REASON);
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account",String.class)).isEqualTo("CHARGE_CLOSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement",Integer.class)).isZero();
    }
    @Test void partialUnusedRefundsAreCumulativeAndCannotReverseAllocatedCash() {
        run("allocate"); refund("first-return","12.34");
        assertThat(totals().unallocatedDeposit()).isEqualByComparingTo("7.66");
        assertThatThrownBy(() -> refund("over-return","7.67")).isInstanceOf(BillingRuleException.class);
        refund("last-return","7.66");
        assertThatThrownBy(() -> refund("funded-cash","0.01")).isInstanceOf(BillingRuleException.class);
        assertThat(totals().completedRefunds()).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_allocation",Integer.class)).isEqualTo(2);
    }
    @Test void refundCannotBorrowUnusedBudgetFromAnotherDeposit() {
        run("allocate"); UUID other=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment_transaction(transaction_id,account_id,transaction_type,classification,status,
                    amount,currency,payment_method,idempotency_key,completed_at)
                VALUES (?,?,'PAYMENT','ADMISSION_DEPOSIT','COMPLETED',100,'VND','CASH',?,'2026-09-28T04:00:00Z')
                """,other,account,other.toString());
        assertThatThrownBy(() -> refund("borrow","20.01")).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_refund_evidence",Integer.class)).isZero();
        assertThat(totals().completedRefunds()).isEqualByComparingTo("0");
    }
    @Test void refundOutboxFailureRollsBackCashAuditAndVersionWithoutTouchingAllocations() {
        run("allocate"); long version=jdbc.queryForObject("SELECT version FROM billing_account",Long.class);
        assertThatThrownBy(() -> refund("retry","20",new HeldOnlyLedgerEventPort((id,event) -> {throw new IllegalStateException("outbox test fault");})))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT version FROM billing_account",Long.class)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_refund_evidence",Integer.class)).isZero();
        assertThat(totals().unallocatedDeposit()).isEqualByComparingTo("20");
        refund("retry","20"); assertThat(totals().unallocatedDeposit()).isEqualByComparingTo("0");
    }
    @Test void exactRefundReplayAfterFinalizationIsNoEffectButNewRefundRequiresReopen() {
        run("allocate"); var recorded=refund("before-final","20");
        jdbc.update("UPDATE billing_account SET status='SETTLED'"); long version=jdbc.queryForObject("SELECT version FROM billing_account",Long.class);
        assertThat(refund("before-final","20")).isEqualTo(recorded);
        assertThatThrownBy(() -> refund("after-final","1")).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT version FROM billing_account",Long.class)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_refund_evidence",Integer.class)).isOne();
    }
    @Test void allocationAndExcessRefundRaceCannotSpendSameOriginalCashTwice() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var go=new CountDownLatch(1);
            var allocation=pool.submit(() -> {
                if(!go.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");
                for(int retry=0;retry<3;retry++) {
                    try{run("allocate-race");return "ALLOCATED";}
                    catch(org.springframework.dao.ConcurrencyFailureException conflict){if(retry==2)throw conflict;}
                    catch(BillingRuleException rejected){return "REJECTED";}
                }
                throw new IllegalStateException("retry exhausted");
            });
            var returned=pool.submit(() -> {
                if(!go.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");
                try{refund("return-race","30");return "REFUNDED";}
                catch(BillingRuleException rejected){return "REJECTED";}
            }); go.countDown();
            assertThat(java.util.List.of(allocation.get(15,TimeUnit.SECONDS),returned.get(15,TimeUnit.SECONDS)))
                    .contains("REJECTED").containsAnyOf("ALLOCATED","REFUNDED");
        }
        assertThat(totals().unallocatedDeposit()).isNotNegative();
        assertThat(totals().netAllocatedPayments().add(totals().completedRefunds())).isLessThanOrEqualTo(new BigDecimal("100"));
    }
    @Test void staleRepeatableReadAllocationRetriesInsteadOfIgnoringCommittedRefund() throws Exception {
        try(var pool=Executors.newSingleThreadExecutor()) {
            var snapshot=new CountDownLatch(1); var returned=new CountDownLatch(1);
            var stale=pool.submit(() -> {
                var tx=new TransactionTemplate(new DataSourceTransactionManager(source));
                tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); tx.setTimeout(10);
                return tx.execute(status -> {
                    jdbc.queryForObject("SELECT count(*) FROM payment_transaction",Integer.class); snapshot.countDown();
                    try{if(!returned.await(5,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}
                    catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException(failure);}
                    return new AdmissionDepositAllocationService(new AdmissionDepositAllocationStoreAdapter(jdbc,mapper),
                            new AdmissionLedgerReadAdapter(jdbc),new LedgerEventOutboxAdapter(jdbc,mapper)).allocate(
                            new AllocateAdmissionDepositCommand(account,original,actor,"stale",Map.of(first,new BigDecimal("48"),second,new BigDecimal("32"))));
                });
            });
            assertThat(snapshot.await(5,TimeUnit.SECONDS)).isTrue();
            try{refund("committed-return","30");}finally{returned.countDown();}
            assertThatThrownBy(() -> stale.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(org.springframework.dao.ConcurrencyFailureException.class);
        }
        assertThatThrownBy(() -> run("stale")).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_allocation",Integer.class)).isZero();
        assertThat(totals().unallocatedDeposit()).isEqualByComparingTo("70");
    }
    @Test void foreignChargeAllocationFailsClosedBeforeReturningCash() {
        run("allocate"); jdbc.update("UPDATE charge SET patient_id=? WHERE charge_id=?",UUID.randomUUID(),first);
        assertThatThrownBy(() -> refund("inconsistent","20")).isInstanceOf(BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_refund_evidence",Integer.class)).isZero();
    }
}
