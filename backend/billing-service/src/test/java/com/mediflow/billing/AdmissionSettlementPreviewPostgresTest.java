package com.mediflow.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import com.mediflow.billing.application.service.AdmissionSettlementPreviewService;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.infrastructure.persistence.adapter.AdmissionLedgerReadAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class AdmissionSettlementPreviewPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private static DataSource source;
    private static JdbcTemplate jdbc;
    private UUID account, admission, patient, department, charge;
    @BeforeAll static void migrate() {
        source = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()); jdbc = new JdbcTemplate(source);
        Flyway.configure().dataSource(source).load().migrate();
    }
    @BeforeEach void setup() {
        // Private ephemeral Testcontainers DB only, never the development database.
        jdbc.execute("TRUNCATE BILLING_ACCOUNT,BILLING_EVENT_OUTBOX CASCADE");
        account = UUID.randomUUID(); admission = UUID.randomUUID(); patient = UUID.randomUUID(); department = UUID.randomUUID(); charge = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,status,currency,opened_at,charge_closed_at)
                VALUES (?,?,?,'ADMISSION',?,'CHARGE_CLOSED','VND',now()-interval '2 hours',now()-interval '1 hour')
                """, account, patient, department, admission);
        jdbc.update("""
                INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,description,quantity,unit_amount,gross_amount,incurred_at)
                VALUES (?,?,?,?,'PRESCRIPTION',?,'RX-DEFAULT','fixture',1,100,100,now()-interval '2 hours')
                """, charge, account, patient, department, UUID.randomUUID());
    }
    private TransactionTemplate transaction() {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setReadOnly(true); tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); tx.setTimeout(5); return tx;
    }
    private com.mediflow.billing.application.dto.response.AdmissionSettlementPreviewDTO preview(JdbcTemplate template) {
        return transaction().execute(status -> new AdmissionSettlementPreviewService(new AdmissionLedgerReadAdapter(template)).get(account));
    }
    private UUID payment(UUID owningAccount, String classification, String amount) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,transaction_type,classification,status,amount,currency,payment_method,idempotency_key,completed_at)
                VALUES (?,?,'PAYMENT',?,'COMPLETED',?,'VND','CASH',?,now()-interval '30 minutes')
                """, id, owningAccount, classification, new BigDecimal(amount), UUID.randomUUID().toString());
        return id;
    }
    private void allocation(UUID transaction, UUID selectedCharge, String amount) {
        jdbc.update("INSERT INTO PAYMENT_ALLOCATION(allocation_id,transaction_id,charge_id,amount) VALUES (?,?,?,?)",
                UUID.randomUUID(), transaction, selectedCharge, new BigDecimal(amount));
    }
    @Test void preview_persistedInstallmentsAndRefunds_reconcilesWithoutMutatingLedgerOrOutbox() {
        var first = payment(account, "SERVICE_PAYMENT", "60");
        var second = payment(account, "SERVICE_PAYMENT", "40");
        allocation(first, charge, "60"); allocation(second, charge, "40"); var returned = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,transaction_type,classification,status,amount,currency,payment_method,idempotency_key,original_transaction_id,completed_at)
                VALUES (?,?,'REFUND','SERVICE_PAYMENT','COMPLETED',15,'VND','CASH',?,?,now()-interval '5 minutes')
                """, returned, account, UUID.randomUUID().toString(), first);
        allocation(returned, charge, "15");
        var before = jdbc.queryForList("SELECT * FROM PAYMENT_TRANSACTION ORDER BY transaction_id");
        var result = preview(jdbc); assertThat(result.completedPayments()).isEqualByComparingTo("100");
        assertThat(result.completedRefunds()).isEqualByComparingTo("15"); assertThat(result.netAllocatedPayments()).isEqualByComparingTo("85");
        assertThat(result.balance()).isEqualByComparingTo("15"); assertThat(result.finalSettlement()).isFalse();
        assertThat(jdbc.queryForList("SELECT * FROM PAYMENT_TRANSACTION ORDER BY transaction_id")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM SETTLEMENT", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM BILLING_EVENT_OUTBOX", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM BILLING_ACCOUNT WHERE account_id=?", String.class, account)).isEqualTo("CHARGE_CLOSED");
    }
    @Test void preview_depositWithoutAllocation_doesNotReduceBalanceOrCreateEarnedRevenue() {
        payment(account, "ADMISSION_DEPOSIT", "150");
        var result = preview(jdbc); assertThat(result.unallocatedDeposit()).isEqualByComparingTo("150");
        assertThat(result.balance()).isEqualByComparingTo("100"); assertThat(result.netAllocatedPayments()).isZero();
    }
    @Test void preview_wrongCurrency_isNotConvertedOrHidden() {
        var transaction = payment(account, "SERVICE_PAYMENT", "100");
        jdbc.update("UPDATE PAYMENT_TRANSACTION SET currency='USD' WHERE transaction_id=?", transaction);
        assertThatThrownBy(() -> preview(jdbc)).isInstanceOf(BillingRuleException.class);
    }
    @Test void preview_crossAccountAllocation_notFilteredIntoPlausibleTotal() {
        UUID other = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                VALUES (?,?,?,'ADMISSION',?,'VND',now())
                """, other, patient, department, UUID.randomUUID());
        allocation(payment(other, "SERVICE_PAYMENT", "100"), charge, "100");
        assertThatThrownBy(() -> preview(jdbc)).isInstanceOf(BillingRuleException.class);
    }
    @Test void preview_samePatientOtherEpisode_isNeverMerged() {
        UUID other = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                VALUES (?,?,?,'ADMISSION',?,'VND',now())
                """, other, patient, department, UUID.randomUUID());
        payment(other, "ADMISSION_DEPOSIT", "999");
        assertThat(preview(jdbc).completedPayments()).isZero();
    }
    @Test void preview_lateCommitBetweenAccountAndMoneyQueries_usesOneFrozenSnapshot() {
        var intercepted = new JdbcTemplate(source) {
            private final AtomicBoolean first = new AtomicBoolean();
            @Override public <T> java.util.List<T> query(PreparedStatementCreator statement, RowMapper<T> mapper) {
                var result = super.query(statement, mapper);
                if (first.compareAndSet(false, true)) {
                    // Separate real connection, committed after the preview's first snapshot read.
                    var outside = new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
                    outside.update("""
                            INSERT INTO PAYMENT_TRANSACTION(transaction_id,account_id,transaction_type,classification,status,amount,currency,payment_method,idempotency_key,completed_at)
                            VALUES (?,?,'PAYMENT','ADMISSION_DEPOSIT','COMPLETED',150,'VND','CASH',?,now())
                            """, UUID.randomUUID(), account, UUID.randomUUID().toString());
                }
                return result;
            }
        };
        assertThat(preview(intercepted).completedPayments()).isZero();
        assertThat(preview(jdbc).completedPayments()).isEqualByComparingTo("150");
    }
    @Test void preview_readCommittedCaller_failsBeforePartialSnapshot() {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        assertThatThrownBy(() -> tx.execute(status -> new AdmissionLedgerReadAdapter(jdbc).read(account)))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test void preview_openAccount_notFinalizable() {
        jdbc.update("UPDATE BILLING_ACCOUNT SET status='OPEN',charge_closed_at=null WHERE account_id=?", account);
        assertThatThrownBy(() -> preview(jdbc)).isInstanceOf(BillingRuleException.class).hasMessage("Admission charges are not closed");
    }
    @Test void preview_outpatientAccount_rejectsWrongEpisode() {
        jdbc.update("UPDATE BILLING_ACCOUNT SET care_episode_type='OUTPATIENT_VISIT' WHERE account_id=?", account);
        assertThatThrownBy(() -> preview(jdbc)).isInstanceOf(BillingRuleException.class).hasMessage("Settlement preview requires an admission account");
    }
    @Test void preview_exceedsRowBudget_neverTruncatesFinancialTotals() {
        jdbc.update("""
                INSERT INTO CHARGE(charge_id,account_id,patient_id,department_id,source_type,source_id,price_code,description,quantity,unit_amount,gross_amount,incurred_at)
                SELECT gen_random_uuid(),?,?,?,'PRESCRIPTION',gen_random_uuid(),'RX-DEFAULT','fixture',1,1,1,now() FROM generate_series(1,10000)
                """, account, patient, department);
        assertThatThrownBy(() -> preview(jdbc)).isInstanceOf(BillingRuleException.class)
                .hasMessage("Admission ledger exceeds the bounded preview; no partial total was returned");
    }
    @Test void preview_transactionCannotWrite_evenAccidentally() {
        assertThatThrownBy(() -> transaction().execute(status -> jdbc.update("UPDATE BILLING_ACCOUNT SET status='SETTLED' WHERE account_id=?", account)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void preview_configuredUseCase_suspendsWeakerOuterTransactionAndUsesItsOwnReadOnlySnapshot() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(com.mediflow.billing.infrastructure.config.AdmissionSettlementPreviewConfiguration.class,
                        AdmissionLedgerReadAdapter.class, TransactionConfiguration.class)
                .withBean(JdbcTemplate.class, () -> jdbc)
                .withBean(org.springframework.transaction.PlatformTransactionManager.class, () -> new DataSourceTransactionManager(source))
                .withPropertyValues("mediflow.billing.ledger.enabled=true", "mediflow.billing.settlement-preview.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var useCase = context.getBean(com.mediflow.billing.application.port.in.GetAdmissionSettlementPreviewUseCase.class);
                    var outer = new TransactionTemplate(new DataSourceTransactionManager(source));
                    outer.executeWithoutResult(status -> {
                        // Uncommitted outer writes must not leak into a preview returned to another caller.
                        payment(account, "ADMISSION_DEPOSIT", "100");
                        assertThat(useCase.get(account).completedPayments()).isZero();
                        status.setRollbackOnly();
                    });
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM PAYMENT_TRANSACTION", Long.class)).isZero();
                    assertThat(useCase.get(account).snapshotOnly()).isTrue();
                });
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @org.springframework.transaction.annotation.EnableTransactionManagement
    static class TransactionConfiguration { }
}
