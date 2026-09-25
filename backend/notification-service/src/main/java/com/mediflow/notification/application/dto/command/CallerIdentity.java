package com.mediflow.notification.application.dto.command;

import java.util.Objects;
import java.util.UUID;

/**
 * Authenticated human identity crossing the JWT filter/controller boundary.
 *
 * <p>{@code accountId} is always the JWT {@code sub} claim. {@code patientId} is the signed
 * {@code patientId} claim and is only ever present for a {@code PATIENT} caller; it is never
 * inferred from {@code accountId} (docs/handoffs/care-finance/CONTRACT-IDENTITY-LOOKUP-01.md).
 * A {@code PATIENT} token missing the claim carries {@code patientId = null} instead of falling
 * back to {@code accountId}, so an ownership check fails closed rather than matching the wrong id.
 *
 * @param accountId JWT subject account identifier
 * @param patientId signed patient identifier, present only for role {@code PATIENT}
 * @param role normalized role name
 */
public record CallerIdentity(UUID accountId, UUID patientId, String role) {

    public CallerIdentity {
        Objects.requireNonNull(accountId, "accountId is required");
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
    }

    /** Keeps Spring Security's {@code Authentication#getName()} compatible with the JWT subject. */
    @Override
    public String toString() {
        return accountId.toString();
    }
}
