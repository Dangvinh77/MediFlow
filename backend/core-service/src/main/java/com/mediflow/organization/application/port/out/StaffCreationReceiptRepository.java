package com.mediflow.organization.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Idempotency receipts persisted within the Organization transaction. */
public interface StaffCreationReceiptRepository {
    void lockKey(UUID actorAccountId, String idempotencyKey);
    Optional<Receipt> find(UUID actorAccountId, String idempotencyKey);
    void save(Receipt receipt);

    record Receipt(UUID actorAccountId, String idempotencyKey, String requestFingerprint, UUID staffId) {}
}
