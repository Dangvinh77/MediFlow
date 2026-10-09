package com.mediflow.pharmacy.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Actual Inpatient fixture bytes through Rabbit -> real transactional Pharmacy projection. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false", "mediflow.jwt.secret=admission-integration-test-secret-at-least-32-bytes",
        "mediflow.features.care-finance-v2=true", "mediflow.pharmacy.admission-consumer.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.publisher-confirm-type=simple",
        "mediflow.pharmacy.outbox.enabled=false", "mediflow.pharmacy.outbox.metrics-enabled=false",
        "mediflow.pharmacy.outbox.maintenance-enabled=false", "mediflow.pharmacy.reservation.release-cron=-",
        "mediflow.pharmacy.reservation.reconciliation-cron=-"
})
@Testcontainers
class AdmissionLifecycleRabbitIntegrationTest {
    private static final String QUEUE = "pharmacy.admission-lifecycle.q", DLQ = "pharmacy.admission-lifecycle.dlq";
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitAdmin admin;
    @Autowired RabbitListenerEndpointRegistry registry;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void cleanAndStartOwnListener() {
        listener().stop();
        admin.purgeQueue(QUEUE); admin.purgeQueue(DLQ);
        jdbc.execute("TRUNCATE admission_medication_event, admission_medication_context");
        listener().start();
    }

    @AfterEach
    void stopOwnListener() { listener().stop(); }

    @Test
    void duplicateStart_commitsOneContextAndNeverChangesStockOrOutbox() throws Exception {
        byte[] body = fixture("admission.started");
        send("admission.started", body); send("admission.started", body);
        drain(1);
        assertThat(version()).isOne();
        assertThat(count("admission_medication_context")).isOne();
        assertNoMedicationEffects();
    }

    @Test
    void medicalDischargeBeforeStart_survivesListenerRestartAndNeverReopensEligibility() throws Exception {
        send("discharge.medically.approved", fixture("discharge.medically.approved"));
        drain(1);
        assertThat(jdbc.queryForObject("SELECT source_started_at IS NULL AND source_medically_discharged_at IS NOT NULL FROM admission_medication_context", Boolean.class)).isTrue();
        listener().start();
        send("admission.started", fixture("admission.started"));
        drain(2);
        assertThat(version()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT source_started_at IS NOT NULL AND source_medically_discharged_at IS NOT NULL FROM admission_medication_context", Boolean.class)).isTrue();
        assertNoMedicationEffects();
    }

    @Test
    void closeBeforeStart_retainsClosedEvidenceAfterLateStart() throws Exception {
        send("admission.closed", fixture("admission.closed")); drain(1);
        listener().start(); send("admission.started", fixture("admission.started")); drain(2);
        assertThat(jdbc.queryForObject("SELECT source_closed_at IS NOT NULL AND source_started_at IS NOT NULL FROM admission_medication_context", Boolean.class)).isTrue();
        assertNoMedicationEffects();
    }

    @Test
    void sameBusinessFactUnderNewEventId_claimsDeliveryWithoutAdvancingContext() throws Exception {
        byte[] original = fixture("admission.started");
        send("admission.started", original); drain(1);
        var root = (ObjectNode) mapper.readTree(original);
        root.put("eventId", UUID.randomUUID().toString());
        listener().start(); send("admission.started", mapper.writeValueAsBytes(root)); drain(2);
        assertThat(version()).isOne();
        assertNoMedicationEffects();
    }

    @Test
    void changedPatient_rollsBackClaimAndRetainsOriginalBytesInDlq() throws Exception {
        send("admission.started", fixture("admission.started")); drain(1);
        var root = (ObjectNode) mapper.readTree(fixture("discharge.medically.approved"));
        ((ObjectNode) root.get("payload")).put("patientId", UUID.randomUUID().toString());
        byte[] wrong = mapper.writeValueAsBytes(root);
        listener().start(); send("discharge.medically.approved", wrong);
        awaitDlq(); listener().stop();
        assertThat(rabbit.receive(DLQ, 5000).getBody()).isEqualTo(wrong);
        assertThat(count("admission_medication_event")).isOne();
        assertThat(version()).isOne();
        assertNoMedicationEffects();
    }

    @Test
    void unknownVersion_goesToDlqWithoutContextOrClaim() throws Exception {
        var root = (ObjectNode) mapper.readTree(fixture("admission.started")); root.put("version", 2);
        byte[] wrong = mapper.writeValueAsBytes(root);
        send("admission.started", wrong); awaitDlq(); listener().stop();
        assertThat(rabbit.receive(DLQ, 5000).getBody()).isEqualTo(wrong);
        assertThat(count("admission_medication_event")).isZero();
        assertThat(count("admission_medication_context")).isZero();
    }

    @Test
    void storageFailure_rollsBackClaimThenRetainedByteReplayCommitsOnce() throws Exception {
        jdbc.execute("CREATE FUNCTION reject_admission_intake() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected write failure'; END $$");
        jdbc.execute("CREATE TRIGGER admission_intake_failure BEFORE INSERT ON admission_medication_context FOR EACH ROW EXECUTE FUNCTION reject_admission_intake()");
        byte[] body = fixture("admission.started");
        try {
            send("admission.started", body); awaitDlq(); listener().stop();
            assertThat(count("admission_medication_event")).isZero();
            assertThat(count("admission_medication_context")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER admission_intake_failure ON admission_medication_context");
            jdbc.execute("DROP FUNCTION reject_admission_intake()");
        }
        var retained = rabbit.receive(DLQ, 5000); assertThat(retained).isNotNull();
        assertThat(retained.getBody()).isEqualTo(body);
        listener().start(); send("admission.started", retained.getBody()); drain(1);
        assertThat(version()).isOne(); assertNoMedicationEffects();
    }

    private org.springframework.amqp.rabbit.listener.MessageListenerContainer listener() {
        return registry.getListenerContainer("pharmacyAdmissionLifecycle");
    }
    private void drain(int deliveries) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(count("admission_medication_event")).isEqualTo(deliveries));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(QUEUE).getMessageCount()).isZero());
        listener().stop();
    }
    private void awaitDlq() { await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(admin.getQueueInfo(DLQ).getMessageCount()).isOne()); }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private long version() { return jdbc.queryForObject("SELECT version FROM admission_medication_context", Long.class); }
    private void assertNoMedicationEffects() {
        assertThat(count("prescription")).isZero(); assertThat(count("dispense_slip")).isZero();
        assertThat(count("payment_receipt")).isZero(); assertThat(count("pharmacy_event_outbox")).isZero();
    }
    private byte[] fixture(String key) throws Exception {
        return Files.readAllBytes(Path.of("../inpatient-service/src/test/resources/contracts/" + key + ".v1.json"));
    }
    private void send(String key, byte[] body) {
        rabbit.invoke(operations -> {
            operations.send("mediflow.events", key, new Message(body, new MessageProperties()));
            operations.waitForConfirmsOrDie(5000); return null;
        });
    }
}
