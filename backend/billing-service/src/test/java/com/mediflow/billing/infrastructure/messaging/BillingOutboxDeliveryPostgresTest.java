package com.mediflow.billing.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Real PG claims/progress with injected publisher confirms; no claim of broker NACK proof. */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({BillingOutboxClaimService.class, BillingOutboxDeliveryPostgresTest.TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class BillingOutboxDeliveryPostgresTest {
    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class TimeConfig {
        @Bean @org.springframework.context.annotation.Primary
        Clock clock() { return mock(Clock.class); }
    }

    @Autowired BillingOutboxClaimService claims;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @BeforeEach void resetOutbox() {
        jdbc.update("DELETE FROM BILLING_EVENT_OUTBOX");
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void negativeConfirmOrTimeout_persistsBoundedRetryAndBlocksSuccessor(boolean timeout) {
        UUID aggregate = UUID.randomUUID();
        UUID first = seed(aggregate, true, 0, NOW.minusSeconds(2));
        UUID second = seed(aggregate, true, 0, NOW.minusSeconds(1));
        String original = payload(first);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            CorrelationData correlation = invocation.getArgument(3);
            assertThat(message.getBody()).isEqualTo(original.getBytes(StandardCharsets.UTF_8));
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(first.toString());
            if (!timeout) correlation.getFuture().complete(new CorrelationData.Confirm(false, "injected NACK"));
            return null;
        }).when(rabbit).send(eq("mediflow.events"), any(), any(Message.class), any(CorrelationData.class));
        var dispatcher = new BillingOutboxDispatcher(rabbit, claims, clock, 100, 10, 1000, 2000, 3,
                new SimpleMeterRegistry());

        for (int attempt = 1; attempt <= 3; attempt++) {
            Instant attemptedAt = clock.instant();
            dispatcher.dispatchPending();
            var row = jdbc.queryForMap("SELECT * FROM BILLING_EVENT_OUTBOX WHERE event_id=?", first);
            assertThat(row.get("attempts")).isEqualTo(attempt);
            assertThat(row.get("published_at")).isNull();
            assertThat(row.get("locked_by")).isNull();
            assertThat(row.get("locked_at")).isNull();
            assertThat(row.get("payload")).isEqualTo(original);
            assertThat(row.get("last_error").toString()).contains(timeout ? "confirm failed" : "nack");
            Instant next = attemptedAt.plusMillis(attempt == 1 ? 1000 : 2000);
            assertThat(((java.sql.Timestamp) row.get("available_at")).toInstant()).isEqualTo(next);
            assertThat(row.get("quarantined_at") != null).isEqualTo(attempt == 3);
            dispatcher.dispatchPending(); // Still before availableAt: no additional send.
            verify(rabbit, times(attempt)).send(any(), any(), any(Message.class), any(CorrelationData.class));
            when(clock.instant()).thenReturn(next);
        }
        dispatcher.dispatchPending(); // Exhausted even though backoff elapsed.
        verify(rabbit, times(3)).send(any(), any(), any(Message.class), any(CorrelationData.class));
        var successor = jdbc.queryForMap("SELECT attempts,published_at,locked_by FROM BILLING_EVENT_OUTBOX WHERE event_id=?", second);
        assertThat(successor.get("attempts")).isEqualTo(0);
        assertThat(successor.get("published_at")).isNull();
        assertThat(successor.get("locked_by")).isNull();
    }

    @Test void reclaimedLease_rejectsOldOwnerSuccessAndFailure() {
        UUID id = seed(UUID.randomUUID(), true, 0, NOW.minusSeconds(1));
        assertThat(claims.claim(10, "old-owner")).extracting(e -> e.getEventId()).containsExactly(id);
        when(clock.instant()).thenReturn(NOW.plusSeconds(31));
        assertThat(claims.claim(10, "new-owner")).extracting(e -> e.getEventId()).containsExactly(id);
        assertThat(claims.markPublished(id, "old-owner", clock.instant())).isFalse();
        assertThat(claims.markFailure(id, "old-owner", "stale failure", clock.instant(), 1)).isFalse();
        var row = jdbc.queryForMap("SELECT attempts,locked_by,published_at,quarantined_at,last_error FROM BILLING_EVENT_OUTBOX WHERE event_id=?", id);
        assertThat(row.get("locked_by")).isEqualTo("new-owner");
        assertThat(row.get("attempts")).isEqualTo(0);
        assertThat(row.get("published_at")).isNull();
        assertThat(row.get("quarantined_at")).isNull();
        assertThat(row.get("last_error")).isNull();
        assertThat(claims.markPublished(id, "new-owner", clock.instant())).isTrue();
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void replay_refusesQuarantinedHeldRowsWithoutChangingProgress(int version) {
        UUID id = seed(UUID.randomUUID(), false, version, NOW.minusSeconds(1));
        jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET attempts=3,quarantined_at=?,last_error='held evidence' WHERE event_id=?",
                java.sql.Timestamp.from(NOW), id);
        var before = jdbc.queryForMap("SELECT * FROM BILLING_EVENT_OUTBOX WHERE event_id=?", id);
        assertThat(claims.replay(id)).isFalse();
        assertThat(jdbc.queryForMap("SELECT * FROM BILLING_EVENT_OUTBOX WHERE event_id=?", id)).isEqualTo(before);
        assertThat(claims.claim(10, "dispatcher")).isEmpty();
    }

    @Test void replay_onlyResetsEligibleQuarantinedProgressAndKeepsOriginalBytes() {
        UUID id = seed(UUID.randomUUID(), true, 0, NOW.minusSeconds(1));
        String original = payload(id);
        assertThat(claims.replay(id)).isFalse(); // Pending is not an operator replay candidate.
        jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET attempts=3,quarantined_at=?,last_error='routing failed' WHERE event_id=?",
                java.sql.Timestamp.from(NOW), id);
        assertThat(claims.replay(id)).isTrue();
        var row = jdbc.queryForMap("SELECT * FROM BILLING_EVENT_OUTBOX WHERE event_id=?", id);
        assertThat(row.get("payload")).isEqualTo(original);
        assertThat(row.get("attempts")).isEqualTo(0);
        assertThat(row.get("quarantined_at")).isNull();
        assertThat(row.get("last_error")).isNull();
        assertThat(claims.claim(10, "publisher")).extracting(e -> e.getEventId()).containsExactly(id);
        assertThat(claims.markPublished(id, "publisher", NOW)).isTrue();
        assertThat(claims.replay(id)).isFalse();
    }

    private UUID seed(UUID aggregate, boolean enabled, int version, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,
                    publication_enabled,contract_version,created_at,available_at)
                VALUES (?,'payment.completed',?,?,?,?,?,?)
                """, id, aggregate, "{\"eventId\":\"" + id + "\"}", enabled, version,
                java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt));
        return id;
    }

    private String payload(UUID id) {
        return jdbc.queryForObject("SELECT payload FROM BILLING_EVENT_OUTBOX WHERE event_id=?", String.class, id);
    }
}
