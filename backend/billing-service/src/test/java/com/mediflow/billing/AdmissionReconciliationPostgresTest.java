package com.mediflow.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.ReconcileAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.SettlementReconciledPayload;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.service.AdmissionReconciliationService;
import com.mediflow.billing.infrastructure.messaging.LedgerEventOutboxAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.AdmissionLedgerReadAdapter;
import com.mediflow.billing.infrastructure.persistence.adapter.AdmissionReconciliationStoreAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class AdmissionReconciliationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static DriverManagerDataSource source;
    private static JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private UUID account,patient,admission,department,charge,actor;
    @BeforeAll static void migrate() {
        source = new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()); jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).load().migrate();
    }
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE billing_account,billing_event_outbox CASCADE");
        account=UUID.randomUUID(); patient=UUID.randomUUID(); admission=UUID.randomUUID(); department=UUID.randomUUID(); charge=UUID.randomUUID(); actor=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO billing_account(account_id,patient_id,department_id,care_episode_type,care_episode_id,status,opened_at,charge_closed_at)
                VALUES (?,?,?,'ADMISSION',?,'CHARGE_CLOSED',now()-interval '2 hours',now()-interval '1 hour')
                """,account,patient,department,admission);
        jdbc.update("""
                INSERT INTO charge(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,description,unit_amount,gross_amount,incurred_at)
                VALUES (?,?,?,?,'PRESCRIPTION',?,'RX','fixture',100,100,now()-interval '2 hours')
                """,charge,account,patient,department,UUID.randomUUID());
    }
    private SettlementReconciledPayload run(String key, LedgerEventPort outbox) {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); tx.setTimeout(5);
        return tx.execute(status -> new AdmissionReconciliationService(new AdmissionLedgerReadAdapter(jdbc),
                new AdmissionReconciliationStoreAdapter(jdbc,mapper),outbox).reconcile(new ReconcileAdmissionCommand(account,actor,key)));
    }
    private SettlementReconciledPayload run(String key) { return run(key,new LedgerEventOutboxAdapter(jdbc,mapper)); }
    private void pay(String amount) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payment_transaction(transaction_id,account_id,transaction_type,classification,status,amount,currency,payment_method,idempotency_key,completed_at)
                VALUES (?,?,'PAYMENT','SERVICE_PAYMENT','COMPLETED',?,'VND','CASH',?,now())
                """,id,account,new BigDecimal(amount),id.toString());
        jdbc.update("INSERT INTO payment_allocation(allocation_id,transaction_id,charge_id,amount) VALUES (?,?,?,?)",UUID.randomUUID(),id,charge,new BigDecimal(amount));
    }
    @Test void reconcile_actualPersistedLedger_writesImmutableNonFinalStockAndHeldOutbox() throws Exception {
        pay("60"); var first=run("first"); pay("40"); var second=run("second");
        assertThat(first.balance()).isEqualByComparingTo("40"); assertThat(second.balance()).isZero();
        assertThat(second.settlementVersion()).isEqualTo(2); assertThat(second.supersedesSettlementId()).isEqualTo(first.settlementId());
        assertThat(run("first")).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_reconciliation",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT publication_enabled FROM billing_event_outbox",Boolean.class)).containsOnly(false).hasSize(2);
        assertThat(jdbc.queryForList("SELECT routing_key FROM billing_event_outbox",String.class)).containsOnly("settlement.reconciled");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM settlement",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account WHERE account_id=?",String.class,account)).isEqualTo("CHARGE_CLOSED");
        var row = mapper.readTree(jdbc.queryForObject("SELECT payload FROM billing_event_outbox ORDER BY created_at LIMIT 1",String.class));
        assertThat(row.path("payload").path("finalSettlement").booleanValue()).isFalse();
    }
    @Test void reconcile_outboxFailure_rollsBackSnapshotAndAccountFence() {
        assertThatThrownBy(() -> run("failure",new HeldOnlyLedgerEventPort((account,event) -> { throw new IllegalStateException("broker serialization failure"); }))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_reconciliation",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM billing_account WHERE account_id=?",Long.class,account)).isZero();
        assertThat(run("failure").settlementVersion()).isOne();
    }
    @Test void reconcile_sameKeyDifferentActor_rejectsWithoutAnotherSnapshot() {
        run("same"); actor=UUID.randomUUID();
        assertThatThrownBy(() -> run("same")).isInstanceOf(com.mediflow.common.exception.DuplicateResourceException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_event_outbox",Integer.class)).isOne();
    }
    @Test void reconcile_openCharges_rejectsWithoutMutation() {
        jdbc.update("UPDATE billing_account SET status='OPEN',charge_closed_at=NULL WHERE account_id=?",account);
        assertThatThrownBy(() -> run("open")).isInstanceOf(com.mediflow.billing.domain.exception.BillingRuleException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_reconciliation",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM billing_account WHERE account_id=?",Long.class,account)).isZero();
    }
    @Test void reconcile_duplicateReplicas_retrySerializationThenReplayOneImmutableVersion() throws Exception {
        try (var pool=Executors.newFixedThreadPool(2)) {
            var ready=new CountDownLatch(2); var go=new CountDownLatch(1);
            java.util.concurrent.Callable<SettlementReconciledPayload> task=() -> {
                ready.countDown(); if (!go.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("test start timed out");
                for (int i=0;i<3;i++) {
                    try { return run("replica"); } catch (org.springframework.dao.ConcurrencyFailureException error) { if (i==2) throw error; }
                }
                throw new IllegalStateException("test retries exhausted");
            };
            var a=pool.submit(task); var b=pool.submit(task); assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admission_reconciliation",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM billing_event_outbox",Integer.class)).isOne();
    }
    @Test void reconcile_replayAfterFinalAccountState_doesNotIssueNewPermission() {
        var original=run("historical"); jdbc.update("UPDATE billing_account SET status='CLOSED' WHERE account_id=?",account);
        assertThat(run("historical")).isEqualTo(original);
        assertThatThrownBy(() -> run("new")).isInstanceOf(com.mediflow.billing.domain.exception.BillingRuleException.class);
    }
}
