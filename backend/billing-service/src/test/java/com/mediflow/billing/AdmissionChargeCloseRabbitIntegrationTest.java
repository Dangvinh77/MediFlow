package com.mediflow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(properties = {"eureka.client.enabled=false", "mediflow.billing.ledger.enabled=true",
        "mediflow.jwt.secret=billing-integration-test-only-key-at-least-32-bytes",
        "mediflow.billing.admission-charge-close.enabled=true", "mediflow.billing.outbox.enabled=false",
        "mediflow.billing.outbox.metrics-enabled=false", "mediflow.billing.outbox.maintenance-enabled=false",
        "mediflow.billing.rabbit.retry.initial-interval-ms=100", "mediflow.billing.rabbit.retry.max-interval-ms=100",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
class AdmissionChargeCloseRabbitIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.rabbitmq.host", MQ::getHost);
        registry.add("spring.rabbitmq.port", MQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", MQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", MQ::getAdminPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired ObjectMapper mapper;

    @BeforeEach void reset() {
        listeners.getListenerContainer("billingEvents").stop();
        admin.purgeQueue("billing.q"); admin.purgeQueue("billing.dlq");
        jdbc.execute("TRUNCATE admission_charge_close,billing_account,billing_event_outbox CASCADE");
    }
    @AfterEach void stop() { listeners.getListenerContainer("billingEvents").stop(); }

    private void account() {
        jdbc.update("""
                INSERT INTO billing_account(account_id,patient_id,department_id,care_episode_type,care_episode_id,opened_at)
                VALUES (?,'00000000-0000-4000-8000-000000000022',?,'ADMISSION',
                    '00000000-0000-4000-8000-000000000021','2026-09-28T02:10:00Z')
                """, UUID.randomUUID(), UUID.randomUUID());
    }

    private void send(byte[] bytes) {
        var properties = new MessageProperties();
        properties.setContentType("application/json");
        rabbit.send("mediflow.events", "discharge.medically.approved", new Message(bytes, properties));
    }

    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }

    private void accepted(int deliveries) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(count("admission_charge_close_delivery")).isEqualTo(deliveries);
            assertThat(admin.getQueueInfo("billing.q").getMessageCount()).isZero();
        });
    }

    private void dlq(byte[] bytes) {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(admin.getQueueInfo("billing.dlq").getMessageCount()).isOne());
        var rejected = rabbit.receive("billing.dlq", 5000);
        assertThat(rejected).isNotNull();
        assertThat(rejected.getBody()).isEqualTo(bytes);
    }

    @Test void actualSourceAndNewDeliveryCloseOnceOnExistingSingleDispatcher() throws Exception {
        account(); listeners.getListenerContainer("billingEvents").start();
        byte[] original = AdmissionChargeCloseDecoderTest.fixture(); send(original); accepted(1);
        send(original);
        var root = (ObjectNode) mapper.readTree(original); root.put("eventId", UUID.randomUUID().toString());
        send(mapper.writeValueAsBytes(root)); accepted(2);
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account", String.class)).isEqualTo("CHARGE_CLOSED");
        assertThat(jdbc.queryForObject("SELECT version FROM billing_account", Long.class)).isOne();
        assertThat(count("settlement")).isZero(); assertThat(count("billing_event_outbox")).isZero();
    }

    @Test void durableEarlySourceSurvivesListenerRestartAndReplaysAgainstExactAccount() throws Exception {
        listeners.getListenerContainer("billingEvents").start();
        byte[] bytes = AdmissionChargeCloseDecoderTest.fixture(); send(bytes); accepted(1);
        assertThat(jdbc.queryForObject("SELECT status FROM admission_charge_close", String.class)).isEqualTo("PENDING_ACCOUNT");
        listeners.getListenerContainer("billingEvents").stop(); account();
        listeners.getListenerContainer("billingEvents").start(); send(bytes);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT status FROM admission_charge_close", String.class)).isEqualTo("APPLIED"));
        assertThat(count("admission_charge_close_delivery")).isOne();
    }

    @Test void unknownMedicalRevisionRetainsOriginalBytesInDlqWithNoEffects() throws Exception {
        account();
        var root = (ObjectNode) mapper.readTree(AdmissionChargeCloseDecoderTest.fixture());
        ((ObjectNode) root.path("payload")).put("sourceRevision", 2);
        byte[] invalid = mapper.writeValueAsBytes(root);
        listeners.getListenerContainer("billingEvents").start(); send(invalid); dlq(invalid);
        assertThat(count("admission_charge_close_delivery")).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account", String.class)).isEqualTo("OPEN");
    }

    @Test void databaseFailureExhaustsBoundedRetryRollsBackThenOriginalDlqBytesRecover() throws Exception {
        account();
        jdbc.execute("CREATE FUNCTION reject_medical_close() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$");
        jdbc.execute("CREATE TRIGGER reject_medical_close BEFORE UPDATE ON admission_charge_close FOR EACH ROW EXECUTE FUNCTION reject_medical_close()");
        byte[] bytes = AdmissionChargeCloseDecoderTest.fixture();
        listeners.getListenerContainer("billingEvents").start();
        try {
            send(bytes); dlq(bytes);
            assertThat(count("admission_charge_close")).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM billing_account", String.class)).isEqualTo("OPEN");
        } finally {
            jdbc.execute("DROP TRIGGER reject_medical_close ON admission_charge_close");
            jdbc.execute("DROP FUNCTION reject_medical_close()");
        }
        send(bytes); accepted(1);
        assertThat(jdbc.queryForObject("SELECT status FROM billing_account", String.class)).isEqualTo("CHARGE_CLOSED");
    }
}
