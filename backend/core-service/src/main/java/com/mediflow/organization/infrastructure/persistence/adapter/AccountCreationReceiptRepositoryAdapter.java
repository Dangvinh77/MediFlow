package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.organization.application.port.out.AccountCreationReceiptRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountCreationReceiptRepositoryAdapter implements AccountCreationReceiptRepository {
    private final JdbcTemplate jdbc;

    public AccountCreationReceiptRepositoryAdapter(@Qualifier("organizationDataSource") DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    @Override
    public void lockKey(UUID actorAccountId, String idempotencyKey) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) resultSet -> {
                    resultSet.next();
                    return null;
                }, "account:" + actorAccountId + ":" + idempotencyKey);
    }

    @Override
    public Optional<Receipt> find(UUID actorAccountId, String idempotencyKey) {
        return jdbc.query("""
                SELECT actor_account_id,idempotency_key,request_fingerprint,password_hash,account_id
                FROM account_creation_receipt WHERE actor_account_id=? AND idempotency_key=?
                """, (row, index) -> new Receipt(row.getObject("actor_account_id", UUID.class),
                row.getString("idempotency_key"), row.getString("request_fingerprint"),
                row.getString("password_hash"), row.getObject("account_id", UUID.class)), actorAccountId, idempotencyKey)
                .stream().findFirst();
    }

    @Override
    public void save(Receipt receipt) {
        jdbc.update("""
                INSERT INTO account_creation_receipt
                    (actor_account_id,idempotency_key,request_fingerprint,password_hash,account_id,created_at)
                VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)
                """, receipt.actorAccountId(), receipt.idempotencyKey(), receipt.requestFingerprint(),
                receipt.passwordHash(), receipt.accountId());
    }
}
