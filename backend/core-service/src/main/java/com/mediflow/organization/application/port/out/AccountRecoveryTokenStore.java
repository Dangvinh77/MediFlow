package com.mediflow.organization.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccountRecoveryTokenStore {
    void invalidateUnused(UUID accountId, Instant at);
    void save(UUID tokenId, UUID accountId, String tokenHash, long credentialVersion, Instant expiresAt);
    Optional<RecoveryToken> find(String tokenHash);
    Optional<RecoveryToken> findForUpdate(String tokenHash);
    boolean markUsed(UUID tokenId, Instant at);

    record RecoveryToken(UUID tokenId, UUID accountId, String tokenHash, long credentialVersion,
            Instant expiresAt, Instant usedAt) { }
}
