package com.mediflow.report.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Shared writers do not serialize each other; the rare switch exclusively fences all sources. */
final class FinancialPublicationFence {
    private static final String KEY = "report-financial-publication";
    private FinancialPublicationFence() { }
    static void writer(JdbcTemplate jdbc) {
        transaction();
        jdbc.queryForList("SELECT pg_advisory_xact_lock_shared(hashtextextended(?,0))", KEY);
    }
    static void publisher(JdbcTemplate jdbc) {
        transaction();
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        jdbc.execute("SET LOCAL statement_timeout='5s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", KEY);
    }
    private static void transaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Publication fence requires a transaction");
    }
}
