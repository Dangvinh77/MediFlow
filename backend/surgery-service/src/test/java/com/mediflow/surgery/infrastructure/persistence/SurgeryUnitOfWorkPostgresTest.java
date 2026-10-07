package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.surgery.application.exception.SurgeryCommandBusyException;
import com.mediflow.surgery.infrastructure.persistence.adapter.SurgeryUnitOfWorkAdapter;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL 55P03/40P01 and rollback, no manufactured lock exceptions. */
@Testcontainers
class SurgeryUnitOfWorkPostgresTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private SurgeryUnitOfWorkAdapter work;
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword());
        jdbc=new JdbcTemplate(source); manager=new DataSourceTransactionManager(source); work=new SurgeryUnitOfWorkAdapter(jdbc,manager);
        jdbc.execute("CREATE TABLE IF NOT EXISTS lock_probe(id integer PRIMARY KEY, value integer NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS attempt_effect(id integer PRIMARY KEY)");
        jdbc.execute("TRUNCATE lock_probe, attempt_effect"); jdbc.update("INSERT INTO lock_probe VALUES(1,0),(2,0)");
    }
    @Test void outsideSuspendsEvenCallingTransactionAndReadIsIndependentReadOnly() {
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            work.outside(() -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                work.read(() -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                    return null;
                });
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return null;
            });
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        });
    }
    @Test void realLockTimeoutExhaustsExactlyThreeAttemptsAndRollsBackAllEffects() throws Exception {
        var locked=new CountDownLatch(1); var release=new CountDownLatch(1);
        var attempts=new AtomicInteger();
        try(var pool=Executors.newSingleThreadExecutor()) {
            var owner=pool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                jdbc.queryForObject("SELECT value FROM lock_probe WHERE id=1 FOR UPDATE",Integer.class);
                locked.countDown(); await(release); return null;
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            long started=System.nanoTime();
            try {
                assertThatThrownBy(() -> work.write(() -> {
                    int attempt=attempts.incrementAndGet(); jdbc.update("INSERT INTO attempt_effect VALUES(?)",attempt);
                    jdbc.update("UPDATE lock_probe SET value=value+1 WHERE id=1"); return null;
                })).isInstanceOf(SurgeryCommandBusyException.class);
                assertThat(attempts).hasValue(3);
                assertThat(Duration.ofNanos(System.nanoTime()-started)).isBetween(Duration.ofSeconds(2),Duration.ofSeconds(12));
                assertThat(jdbc.queryForObject("SELECT count(*) FROM attempt_effect",Integer.class)).isZero();
            } finally { release.countDown(); owner.get(5,TimeUnit.SECONDS); }
        }
        assertThat(jdbc.queryForObject("SELECT value FROM lock_probe WHERE id=1",Integer.class)).isZero();
        assertThat(work.write(() -> {
            jdbc.update("INSERT INTO attempt_effect VALUES(1)");
            jdbc.update("UPDATE lock_probe SET value=value+1 WHERE id=1"); return 1;
        })).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attempt_effect",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT value FROM lock_probe WHERE id=1",Integer.class)).isOne();
    }
    @Test void nonLockFailureIsNotRetriedAndWriteIsAtomic() {
        var attempts=new AtomicInteger();
        assertThatThrownBy(() -> work.write(() -> {
            attempts.incrementAndGet(); jdbc.update("INSERT INTO attempt_effect VALUES(1)");
            throw new IllegalArgumentException("business failure");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(attempts).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attempt_effect",Integer.class)).isZero();
    }
    @Test void realDeadlockVictimRetriesFreshTransactionAndBothWorkersCommitOnce() throws Exception {
        var firstLocks=new CountDownLatch(2); var deadlocks=new AtomicInteger();
        var attempts=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var one=pool.submit(() -> deadlockCommand(1,2,firstLocks,deadlocks,attempts));
            var two=pool.submit(() -> deadlockCommand(2,1,firstLocks,deadlocks,attempts));
            one.get(15,TimeUnit.SECONDS); two.get(15,TimeUnit.SECONDS);
        }
        assertThat(deadlocks.get()).isGreaterThanOrEqualTo(1);
        assertThat(attempts.get()).isBetween(3,6);
        assertThat(jdbc.queryForList("SELECT value FROM lock_probe ORDER BY id",Integer.class)).containsExactly(2,2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attempt_effect",Integer.class)).isEqualTo(2);
    }
    private Void deadlockCommand(int first,int second,CountDownLatch firstLocks,AtomicInteger deadlocks,AtomicInteger attempts) {
        var ownAttempt=new AtomicInteger();
        return work.write(() -> {
            attempts.incrementAndGet(); int number=ownAttempt.incrementAndGet();
            jdbc.execute("SET LOCAL deadlock_timeout = '100ms'");
            jdbc.update("INSERT INTO attempt_effect VALUES(?)",first);
            jdbc.update("UPDATE lock_probe SET value=value+1 WHERE id=?",first);
            if(number==1) { firstLocks.countDown(); await(firstLocks); }
            try { jdbc.update("UPDATE lock_probe SET value=value+1 WHERE id=?",second); }
            catch(RuntimeException failure) {
                for(Throwable cause=failure;cause!=null;cause=cause.getCause())
                    if(cause instanceof SQLException sql && "40P01".equals(sql.getSQLState())) deadlocks.incrementAndGet();
                throw failure;
            }
            return null;
        });
    }
    private static void await(CountDownLatch latch) {
        try { if(!latch.await(8,TimeUnit.SECONDS)) throw new AssertionError("Race barrier timed out"); }
        catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
}
