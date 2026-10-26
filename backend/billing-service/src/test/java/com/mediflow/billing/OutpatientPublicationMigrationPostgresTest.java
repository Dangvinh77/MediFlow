package com.mediflow.billing;

import java.math.BigDecimal;
import java.util.UUID;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/**
 * L2 migration/fence proof (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md §4). Uses only an
 * isolated PostgreSQL container, never repairs an existing developer database.
 */
@Testcontainers
class OutpatientPublicationMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    private DataSource source;
    private JdbcTemplate jdbc;

    @BeforeEach void freshContainer() {
        source = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        jdbc = new JdbcTemplate(source);
        flyway("latest").clean();
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration").target(target).cleanDisabled(false).load();
    }

    @Test void freshDatabase_migratesToLatestAndCreatesCohortTable() {
        var flyway = flyway("latest");
        flyway.migrate();
        flyway.validate();
        assertThat(jdbc.queryForObject("SELECT to_regclass('billing_outpatient_publication_cohort') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT version) FROM flyway_schema_history WHERE type='SQL' AND success", Integer.class))
                .isEqualTo(32);
        assertThat(jdbc.queryForObject("SELECT to_regclass('billing_outpatient_refund_publication') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT to_regclass('lab_zero_payable_eligibility') IS NOT NULL", Boolean.class)).isTrue();
    }

    @Test void upgradeFromV21_preservesExistingHeldRowBytesAndLeavesCohortLinkNull() {
        flyway("21").migrate();
        UUID account = seedAccountAndRequest();
        UUID eventId = UUID.randomUUID();
        String payload = "{\"eventId\":\"" + eventId + "\",\"eventType\":\"payment.completed\"}";
        jdbc.update("""
                INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version)
                VALUES (?,?,?,?,FALSE,1)
                """, eventId, "payment.completed", account, payload);

        flyway("latest").migrate();

        var row = jdbc.queryForMap(
                "SELECT routing_key,aggregate_id,payload,publication_enabled,contract_version,outpatient_payment_request_id FROM BILLING_EVENT_OUTBOX WHERE event_id=?",
                eventId);
        assertThat(row.get("payload")).isEqualTo(payload);
        assertThat(row.get("publication_enabled")).isEqualTo(Boolean.FALSE);
        assertThat(row.get("contract_version")).isEqualTo(1);
        assertThat(row.get("outpatient_payment_request_id")).isNull();
    }

    @Test void directSqlCannotFlipAnExistingHeldRowToPublishable() {
        flyway("latest").migrate();
        UUID account = seedAccountAndRequest();
        UUID eventId = insertHeld(account, "payment.completed");
        assertThatThrownBy(() -> jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET publication_enabled=TRUE WHERE event_id=?", eventId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void directSqlCannotRewritePayloadRoutingKeyOrAggregateAfterInsert() {
        flyway("latest").migrate();
        UUID account = seedAccountAndRequest();
        UUID eventId = insertHeld(account, "payment.completed");
        assertThatThrownBy(() -> jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET payload='{\"tampered\":true}' WHERE event_id=?", eventId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET routing_key='payment.refunded' WHERE event_id=?", eventId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET aggregate_id=? WHERE event_id=?", UUID.randomUUID(), eventId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void dispatcherProgressColumnsRemainMutableAfterInsert() {
        flyway("latest").migrate();
        UUID account = seedAccountAndRequest();
        UUID eventId = insertHeld(account, "payment.completed");
        jdbc.update("""
                UPDATE BILLING_EVENT_OUTBOX SET attempts=attempts+1, last_error='retry-1', available_at=now(),
                       locked_at=now(), locked_by='worker-1', quarantined_at=now(), published_at=now()
                WHERE event_id=?
                """, eventId);
        assertThat(jdbc.queryForObject("SELECT attempts FROM BILLING_EVENT_OUTBOX WHERE event_id=?", Integer.class, eventId)).isEqualTo(1);
    }

    @Test void cohortRowIsInsertOnlyNeverUpdatedOrDeleted() {
        flyway("latest").migrate();
        UUID account = seedAccountAndRequest();
        UUID request = jdbc.queryForObject("SELECT payment_request_id FROM PAYMENT_REQUEST WHERE account_id=?", UUID.class, account);
        jdbc.update("""
                INSERT INTO billing_outpatient_publication_cohort(payment_request_id,account_id,purpose,source_id,acceptance_reference)
                VALUES (?,?,'EXAM',?,'ref-1')
                """, request, account, UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE billing_outpatient_publication_cohort SET acceptance_reference='ref-2' WHERE payment_request_id=?", request))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM billing_outpatient_publication_cohort WHERE payment_request_id=?", request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void outboxRowReferencingCohortButWrongAccount_isRejectedAtInsert() {
        flyway("latest").migrate();
        UUID account = seedAccountAndRequest();
        UUID request = enrollCohort(account, "EXAM");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version,outpatient_payment_request_id)
                VALUES (?,?,?,?,TRUE,1,?)
                """, UUID.randomUUID(), "payment.completed", UUID.randomUUID(), "{}", request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void outboxRowReferencingCohortWithDisallowedEventType_isRejectedAtInsert() {
        flyway("latest").migrate();
        UUID account = seedAccountAndRequest();
        UUID request = enrollCohort(account, "EXAM");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version,outpatient_payment_request_id)
                VALUES (?,?,?,?,TRUE,1,?)
                """, UUID.randomUUID(), "payment.refunded", account, "{}", request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void outboxRowReferencingCohortWithEmptyPayload_isRejected() {
        flyway("latest").migrate();
        UUID account = seedAccountAndRequest();
        UUID request = enrollCohort(account, "EXAM");
        UUID eventId = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version,outpatient_payment_request_id)
                VALUES (?,?,?,?,TRUE,1,?)
                """, eventId, "payment.completed", account, "{}", request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void v0CompatibilityRowsPublishUnchangedAndAreAlsoImmutableAfterInsert() {
        flyway("latest").migrate();
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version)
                VALUES (?,?,?,?,TRUE,0)
                """, eventId, "invoice.created", UUID.randomUUID(), "{}");
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM BILLING_EVENT_OUTBOX WHERE event_id=?", Boolean.class, eventId)).isTrue();
        // The general immutability fence is not specific to V1 cohort facts: a V0 row's own
        // eligibility fact is just as fixed once persisted, matching the dispatcher's own contract.
        assertThatThrownBy(() -> jdbc.update("UPDATE BILLING_EVENT_OUTBOX SET publication_enabled=FALSE WHERE event_id=?", eventId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertHeld(UUID account, String routingKey) {
        UUID eventId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version)
                VALUES (?,?,?,?,FALSE,1)
                """, eventId, routingKey, account, "{}");
        return eventId;
    }

    private UUID enrollCohort(UUID account, String purpose) {
        UUID request = jdbc.queryForObject("SELECT payment_request_id FROM PAYMENT_REQUEST WHERE account_id=?", UUID.class, account);
        jdbc.update("""
                INSERT INTO billing_outpatient_publication_cohort(payment_request_id,account_id,purpose,source_id,acceptance_reference)
                VALUES (?,?,?,?,'ref-1')
                """, request, account, purpose, UUID.randomUUID());
        return request;
    }

    private UUID seedAccountAndRequest() {
        UUID account = UUID.randomUUID(), request = UUID.randomUUID(), invoice = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO BILLING_ACCOUNT(account_id,patient_id,department_id,care_episode_type,care_episode_id,currency,opened_at)
                VALUES (?,?,?,'OUTPATIENT_VISIT',?,'VND',now())
                """, account, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        jdbc.update("""
                INSERT INTO PAYMENT_REQUEST(payment_request_id,invoice_id,account_id,purpose,requested_amount,currency)
                VALUES (?,?,?,'EXAM',?,'VND')
                """, request, invoice, account, new BigDecimal("100000.00"));
        return account;
    }
}
