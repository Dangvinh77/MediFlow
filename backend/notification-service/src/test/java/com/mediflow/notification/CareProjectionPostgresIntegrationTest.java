package com.mediflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.notification.messaging.consumer.CareProjectionWireHandler;

/**
 * HANDOFF-NOTIFICATION-CARE-PROJECTIONS acceptance: "Docker proves each enabled producer event
 * reaches one Notification intent through RabbitMQ." Mirrors {@link CareReceiptIntegrationTest}'s
 * style: real Postgres, the wire handler invoked directly (routing itself is already proven by
 * {@code NotificationEventConsumerCareProjectionRoutingTest}). Real producer bytes, same files the
 * unit {@code CareProjectionWireHandlerTest} already reads — no fixture copy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CareProjectionPostgresIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> false);
        registry.add("mediflow.jwt.secret", () -> "notification-care-projection-integration-secret-32-bytes");
        registry.add("eureka.client.enabled", () -> false);
        registry.add("mediflow.notification.care-v1.enabled", () -> true);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired CareProjectionWireHandler handler;

    @BeforeEach void reset() { jdbc.execute("TRUNCATE NOTIFICATION, PROCESSED_EVENT CASCADE"); }

    @Test void admissionDepositRequested_deliversOneRealNotificationRow() throws Exception {
        handler.receive("admission.deposit.requested", fixture("inpatient", "admission.deposit.requested.v1.json"));

        var row = jdbc.queryForMap("SELECT * FROM NOTIFICATION");
        assertThat(row.get("template_key")).isEqualTo("ADMISSION_DEPOSIT_REQUESTED");
        assertThat(row.get("channel")).isEqualTo("IN_APP");
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT publication_enabled FROM NOTIFICATION_EVENT_OUTBOX", Boolean.class)).isFalse();
    }

    @Test void settlementCompleted_deliversOneRealNotificationRow() throws Exception {
        handler.receive("settlement.completed", billingFixture("ledger-v1/settlement-completed.json"));

        var row = jdbc.queryForMap("SELECT * FROM NOTIFICATION");
        assertThat(row.get("template_key")).isEqualTo("SETTLEMENT_COMPLETED");
        assertThat(row.get("channel")).isEqualTo("IN_APP");
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("content").toString()).contains("hoàn tất");
    }

    @Test void duplicateSettlementDelivery_commitsOneIntentOnly() throws Exception {
        byte[] body = billingFixture("ledger-v1/settlement-completed.json");
        handler.receive("settlement.completed", body);
        handler.receive("settlement.completed", body);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM NOTIFICATION", Long.class)).isOne();
    }

    private static byte[] fixture(String module, String fileName) throws Exception {
        return Files.readAllBytes(Path.of("../" + module + "-service/src/test/resources/contracts/" + fileName));
    }

    private static byte[] billingFixture(String relativePath) throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/" + relativePath));
    }
}
