package com.mediflow.pharmacy.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Real opt-in listener + DB transaction; only this class's isolated containers are touched. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false", "mediflow.jwt.secret=clearance-integration-test-secret-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true", "mediflow.pharmacy.clearance-consumer.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "mediflow.pharmacy.outbox.enabled=false", "mediflow.pharmacy.outbox.metrics-enabled=false",
        "mediflow.pharmacy.outbox.maintenance-enabled=false", "mediflow.pharmacy.reservation.release-cron=-",
        "mediflow.pharmacy.reservation.reconciliation-cron=-", "spring.rabbitmq.publisher-confirm-type=simple"
})
@Testcontainers
class PrescriptionClearanceRabbitIntegrationTest {
    private static final String QUEUE = "pharmacy.financial-clearance.q", DLQ = "pharmacy.financial-clearance.dlq";
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void cleanAndStartOwnListener() {
        listener().stop();
        admin.purgeQueue(QUEUE); admin.purgeQueue(DLQ);
        jdbc.execute("TRUNCATE prescription_clearance_event, prescription_clearance, prescription_clearance_target");
        jdbc.execute("TRUNCATE prescription, drug CASCADE");
        listener().start();
    }
    @AfterEach void drainAndStopOwnListener() { listener().stop(); }

    @Test void duplicateProducerGrant_commitsOneDurablePendingProofWithoutAnyDispense() throws Exception {
        byte[] bytes = fixture("prescription");
        send(bytes); send(bytes); drain();
        assertThat(count("prescription_clearance")).isOne();
        assertThat(count("prescription_clearance_event")).isOne();
        assertThat(jdbc.queryForObject("SELECT target_status FROM prescription_clearance", String.class)).isEqualTo("PENDING");
        assertNoClinicalOrMoneyEffects();
        assertThat(ready(DLQ)).isZero();
    }

    @Test void validOtherPurposes_areAcknowledgedWithoutPermissionOrDlq() throws Exception {
        for (String purpose : new String[]{"exam", "lab", "admission", "surgery"}) send(fixture(purpose));
        drain();
        assertThat(count("prescription_clearance")).isZero();
        assertThat(count("prescription_clearance_event")).isZero();
        assertThat(ready(DLQ)).isZero();
        assertNoClinicalOrMoneyEffects();
    }

    @Test void malformedOrWrongKnownPatient_goToDurableDlqAndRollbackEveryClaim() throws Exception {
        byte[] bytes = fixture("prescription");
        var payload = mapper.readTree(bytes).path("payload");
        UUID prescription = UUID.fromString(payload.path("prescriptionId").asText());
        UUID drug = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO drug(drug_id, drug_name, unit, price, stock_quantity, expiry_date)
                VALUES (?, 'Test drug', 'tablet', 100, 10, '2027-10-01')
                """, drug);
        jdbc.update("""
                INSERT INTO prescription(prescription_id, record_id, patient_id, doctor_id, department_id,
                    prescribed_date, total_amount, status, care_contract_version, care_context,
                    care_episode_type, care_episode_id, price_code)
                VALUES (?, ?, ?, ?, ?, '2026-10-05', 100, 'ACTIVE', 1, 'OUTPATIENT', 'OUTPATIENT_VISIT', ?, 'DRUG')
                """, prescription, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.fromString(payload.path("careEpisodeId").asText()));
        jdbc.update("""
                INSERT INTO prescription_line(line_id, prescription_id, drug_id, quantity, unit_price, line_total)
                VALUES (?, ?, ?, 1, 100, 100)
                """, UUID.randomUUID(), prescription, drug);
        send(bytes);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(ready(DLQ)).isOne());
        drain();
        assertThat(rabbit.receive(DLQ, 5000).getBody()).isEqualTo(bytes);
        assertThat(count("prescription_clearance_event")).isZero();
        assertThat(count("prescription_clearance_target")).isZero();
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM drug WHERE drug_id = ?", Integer.class, drug)).isEqualTo(10);
        listener().start();
        var malformed = (ObjectNode) mapper.readTree(bytes);
        ((ObjectNode) malformed.get("payload")).remove("patientId");
        send(mapper.writeValueAsBytes(malformed));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(ready(DLQ)).isOne());
        drain();
        assertThat(count("prescription_clearance_event")).isZero();
        assertThat(count("payment_receipt")).isZero();
        assertThat(count("pharmacy_event_outbox")).isZero();
    }

    @Test void databaseFailure_exhaustsBoundedRetryWithoutAckLossAndRedeliveryCanCommit() throws Exception {
        jdbc.execute("""
                CREATE FUNCTION reject_listener_grant() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected grant storage failure'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER listener_grant_failure BEFORE INSERT ON prescription_clearance
                FOR EACH ROW EXECUTE FUNCTION reject_listener_grant()
                """);
        byte[] bytes = fixture("prescription");
        try {
            send(bytes);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(ready(DLQ)).isOne());
            drain();
            assertThat(count("prescription_clearance_event")).isZero();
            assertThat(count("prescription_clearance_target")).isZero();
            assertThat(count("prescription_clearance")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER listener_grant_failure ON prescription_clearance");
            jdbc.execute("DROP FUNCTION reject_listener_grant()");
        }
        var retained = rabbit.receive(DLQ, 5000);
        assertThat(retained).isNotNull();
        assertThat(retained.getBody()).isEqualTo(bytes);
        listener().start(); send(retained.getBody()); drain();
        assertThat(count("prescription_clearance_event")).isOne();
        assertThat(count("prescription_clearance")).isOne();
        assertThat(ready(DLQ)).isZero();
        assertNoClinicalOrMoneyEffects();
    }

    private void assertNoClinicalOrMoneyEffects() {
        for (String table : new String[]{"prescription", "dispense_slip", "payment_receipt", "pharmacy_event_outbox"})
            assertThat(count(table)).as(table).isZero();
    }
    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() {
        return registry.getListenerContainer("pharmacyPrescriptionClearance");
    }
    private void drain() {
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(ready(QUEUE)).isZero());
        listener().stop(); // Wait for unacknowledged in-flight DB transactions before assertions/reset.
    }
    private int ready(String queue) { return admin.getQueueInfo(queue).getMessageCount(); }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private byte[] fixture(String purpose) throws Exception {
        return Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/ledger-v1/clearance-" + purpose + ".json"));
    }
    private void send(byte[] body) {
        rabbit.invoke(operations -> {
            operations.send("mediflow.events", "financial.clearance.granted", new Message(body, new MessageProperties()));
            operations.waitForConfirmsOrDie(5000);
            return null;
        });
    }
}
