package com.mediflow.surgery.infrastructure.persistence.adapter;

import com.mediflow.surgery.application.port.out.SurgeryUnitOfWorkPort;
import com.mediflow.surgery.application.exception.SurgeryCommandBusyException;
import java.sql.SQLException;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Each retry is a fresh transaction, never a retry inside an aborted PostgreSQL transaction. */
@Component
@Profile("!test")
public class SurgeryUnitOfWorkAdapter implements SurgeryUnitOfWorkPort {
    private static final int MAX_ATTEMPTS = 3;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate reads;
    private final TransactionTemplate writes;
    private final TransactionTemplate suspended;

    public SurgeryUnitOfWorkAdapter(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        reads = transaction(manager, TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        reads.setReadOnly(true);
        writes = transaction(manager, TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        suspended = transaction(manager, TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    private static TransactionTemplate transaction(PlatformTransactionManager manager, int propagation) {
        var template = new TransactionTemplate(manager);
        template.setPropagationBehavior(propagation);
        template.setTimeout(5);
        return template;
    }

    @Override public <T> T read(Supplier<T> action) {
        return reads.execute(status -> action.get());
    }

    @Override public <T> T outside(Supplier<T> action) {
        return suspended.execute(status -> action.get());
    }

    @Override public <T> T write(Supplier<T> action) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return writes.execute(status -> {
                    jdbc.execute("SET LOCAL lock_timeout = '1000ms'");
                    return action.get();
                });
            } catch (RuntimeException failure) {
                if (!retryable(failure)) throw failure;
                if (attempt == MAX_ATTEMPTS) {
                    throw new SurgeryCommandBusyException();
                }
            }
        }
        throw new IllegalStateException("Unreachable retry state");
    }

    private static boolean retryable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && ("40P01".equals(sql.getSQLState()) || "55P03".equals(sql.getSQLState()))) return true;
        }
        return false;
    }
}
