package com.mediflow.organization.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** A creation and its replay receipt commit in the same Organization transaction. */
public interface AccountCreationReceiptRepository {
    void lockKey(UUID actorAccountId, String idempotencyKey);
    Optional<Receipt> find(UUID actorAccountId, String idempotencyKey);
    void save(Receipt receipt);

    record Receipt(UUID actorAccountId, String idempotencyKey, String requestFingerprint,
                   String passwordHash, UUID accountId) {}
}
