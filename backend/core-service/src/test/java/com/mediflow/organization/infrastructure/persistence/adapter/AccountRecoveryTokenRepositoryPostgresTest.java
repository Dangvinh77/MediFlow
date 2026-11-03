package com.mediflow.organization.infrastructure.persistence.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class AccountRecoveryTokenRepositoryPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void recoveryTokenTimestampsRoundTripAndInvalidateAgainstPostgres() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("""
                CREATE TABLE account_recovery_token (
                    token_id UUID PRIMARY KEY,
                    account_id UUID NOT NULL,
                    token_hash VARCHAR(64) NOT NULL UNIQUE,
                    credential_version BIGINT NOT NULL,
                    expires_at TIMESTAMPTZ NOT NULL,
                    used_at TIMESTAMPTZ,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                )
                """);
        var tokens = new AccountRecoveryTokenRepositoryAdapter(source);
        UUID tokenId = UUID.randomUUID(), accountId = UUID.randomUUID();
        Instant expiry = Instant.parse("2030-01-02T03:04:05Z");
        Instant invalidatedAt = Instant.parse("2029-01-02T03:04:05Z");

        tokens.save(tokenId, accountId, "a".repeat(64), 7, expiry);
        var saved = tokens.find("a".repeat(64)).orElseThrow();
        assertThat(saved.expiresAt()).isEqualTo(expiry);
        assertThat(saved.usedAt()).isNull();

        tokens.invalidateUnused(accountId, invalidatedAt);
        assertThat(tokens.find("a".repeat(64)).orElseThrow().usedAt()).isEqualTo(invalidatedAt);

        UUID secondTokenId = UUID.randomUUID();
        Instant usedAt = Instant.parse("2029-01-02T03:05:05Z");
        tokens.save(secondTokenId, accountId, "b".repeat(64), 7, expiry);
        assertThat(tokens.markUsed(secondTokenId, usedAt)).isTrue();
        assertThat(tokens.find("b".repeat(64)).orElseThrow().usedAt()).isEqualTo(usedAt);
    }
}
