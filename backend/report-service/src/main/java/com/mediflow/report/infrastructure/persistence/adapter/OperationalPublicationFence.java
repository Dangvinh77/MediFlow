package com.mediflow.report.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Fences local journal intake only, not producer databases or unreceived broker messages. */
final class OperationalPublicationFence {
    private static final String KEY = "report-operational-publication";
    private OperationalPublicationFence() { }
    static void writer(JdbcTemplate jdbc) {
        transaction();
        jdbc.queryForList("SELECT pg_advisory_xact_lock_shared(hashtextextended(?,0))", KEY);
    }
    static void publisher(JdbcTemplate jdbc) {
        transaction();
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.execute("SET LOCAL statement_timeout='5s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", KEY);
        if (!"read committed".equals(jdbc.queryForObject("SHOW transaction_isolation", String.class))) {
            throw new com.mediflow.report.domain.exception.ReportRuleException(
                    "OPERATIONAL_PUBLICATION_ISOLATION_INVALID", "Fresh committed source visibility required");
        }
    }
    private static void transaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Publication fence requires a transaction");
    }
}
