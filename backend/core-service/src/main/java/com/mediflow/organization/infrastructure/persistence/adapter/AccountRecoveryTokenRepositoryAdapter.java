package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.organization.application.port.out.AccountRecoveryTokenStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountRecoveryTokenRepositoryAdapter implements AccountRecoveryTokenStore {
    private final JdbcTemplate jdbc;
    public AccountRecoveryTokenRepositoryAdapter(@Qualifier("organizationDataSource") DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    @Override public void invalidateUnused(UUID accountId, Instant at) {
        jdbc.update("UPDATE account_recovery_token SET used_at=? WHERE account_id=? AND used_at IS NULL",
                Timestamp.from(at), accountId);
    }

    @Override public void save(UUID tokenId, UUID accountId, String tokenHash, long version, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO account_recovery_token(token_id,account_id,token_hash,credential_version,expires_at,created_at)
                VALUES (?,?,?,?,?,now())
                """, tokenId, accountId, tokenHash, version, Timestamp.from(expiresAt));
    }

    @Override public Optional<RecoveryToken> findForUpdate(String tokenHash) {
        return findByHash(tokenHash, true);
    }

    @Override public Optional<RecoveryToken> find(String tokenHash) {
        return findByHash(tokenHash, false);
    }

    private Optional<RecoveryToken> findByHash(String tokenHash, boolean lock) {
        String sql = """
                SELECT token_id,account_id,token_hash,credential_version,expires_at,used_at
                FROM account_recovery_token WHERE token_hash=?
                """ + (lock ? " FOR UPDATE" : "");
        return jdbc.query(sql, (rs, index) -> new RecoveryToken(rs.getObject("token_id", UUID.class),
                rs.getObject("account_id", UUID.class), rs.getString("token_hash"),
                rs.getLong("credential_version"), rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant()), tokenHash)
                .stream().findFirst();
    }

    @Override public boolean markUsed(UUID tokenId, Instant at) {
        return jdbc.update("UPDATE account_recovery_token SET used_at=? WHERE token_id=? AND used_at IS NULL",
                Timestamp.from(at), tokenId) == 1;
    }
}
