package com.mediflow.report.application.port.out;

import java.util.UUID;

import com.mediflow.report.domain.model.PaymentContribution;

/** Persistence boundary for invoice-keyed payment compensation state. */
public interface PaymentContributionRepositoryPort {

    /**
     * Acquires a transaction-scoped lock for the invoice and returns its contribution.
     *
     * <p>The adapter must use a PostgreSQL advisory transaction lock keyed by
     * {@code invoiceId}, then select the row. If no row exists, it returns a transient
     * {@link PaymentContribution#initialize(UUID)} instance; the {@code NEW} state is
     * never persisted because the database intentionally accepts only durable states.</p>
     */
    PaymentContribution findOrCreateForUpdate(UUID invoiceId);

    PaymentContribution save(PaymentContribution contribution);
}
