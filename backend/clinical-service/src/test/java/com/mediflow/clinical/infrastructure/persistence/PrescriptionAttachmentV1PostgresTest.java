package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.clinical.application.port.out.OutpatientMvpMode;
import com.mediflow.clinical.application.service.ClinicalIntegrationService;
import com.mediflow.clinical.application.service.ConsumerInboxBoundary;
import com.mediflow.clinical.application.service.ExternalResultApplicationService;
import com.mediflow.clinical.infrastructure.config.RabbitConfig;
import com.mediflow.clinical.infrastructure.persistence.adapter.ConsumerInboxPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.ExternalResultPersistenceAdapter;
import com.mediflow.clinical.infrastructure.persistence.adapter.MedicalRecordPersistenceAdapter;
import com.mediflow.clinical.messaging.consumer.ClinicalQueueConsumer;
import com.mediflow.clinical.messaging.consumer.LabResultCreatedConsumer;
import com.mediflow.clinical.messaging.consumer.PrescriptionFilledConsumer;
import com.mediflow.clinical.messaging.consumer.inbox.InboxEvidenceFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real production consumer/use cases/adapters. Seeds only Clinical's target of Pharmacy's original bytes. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@Import({ClinicalQueueConsumer.class, PrescriptionFilledConsumer.class, LabResultCreatedConsumer.class,
        ClinicalIntegrationService.class, ExternalResultApplicationService.class, MedicalRecordPersistenceAdapter.class,
        ExternalResultPersistenceAdapter.class, ConsumerInboxBoundary.class, ConsumerInboxPersistenceAdapter.class,
        InboxEvidenceFactory.class, PrescriptionAttachmentV1PostgresTest.Configuration.class})
class PrescriptionAttachmentV1PostgresTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container
    static final RabbitMQContainer MQ = new RabbitMQContainer("rabbitmq:3.13-alpine");

    @Autowired ClinicalQueueConsumer consumer;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired ObjectMapper mapper;
    private TransactionTemplate tx;
    private byte[] original;
    private ObjectNode root;
    private UUID recordId;
    private UUID prescriptionId;

    @TestConfiguration
    static class Configuration {
        @Bean OutpatientMvpMode mode() { return new OutpatientMvpMode(true); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().findAndRegisterModules(); }
    }

    @BeforeEach
    void prepareClinicalTarget() throws Exception {
        tx = new TransactionTemplate(manager);
        jdbc.execute("TRUNCATE consumer_inbox_operation,consumer_inbox_delivery,attached_result,diagnosis,medical_record,appointment CASCADE");
        original = Files.readAllBytes(Path.of(
                "../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.filled.outpatient.v1.json"));
        root = (ObjectNode) mapper.readTree(original);
        var p = root.path("payload");
        recordId = UUID.fromString(p.path("recordId").asText());
        prescriptionId = UUID.fromString(p.path("prescriptionId").asText());
        UUID patient = UUID.fromString(p.path("patientId").asText());
        UUID department = UUID.fromString(p.path("departmentId").asText());
        UUID episode = UUID.fromString(p.path("careEpisodeId").asText());
        UUID doctor = UUID.randomUUID();
        jdbc.update("INSERT INTO appointment(appointment_id,patient_id,doctor_id,department_id,appointment_date,appointment_time,status) VALUES (?,?,?,?,CURRENT_DATE,'09:00','IN_EXAM')",
                episode, patient, doctor, department);
        jdbc.update("INSERT INTO medical_record(record_id,patient_id,doctor_id,department_id,examination_date,appointment_id) VALUES (?,?,?,?,CURRENT_DATE,?)",
                recordId, patient, doctor, department, episode);
    }

    @Test
    void originalProducerBytesAndNewDeliveryIdAttachOneDispenseAndRetainEvidence() throws Exception {
        deliver(original);
        deliver(original);
        byte[] newDelivery = newDelivery(root);
        deliver(newDelivery);
        assertCounts(2, 1, 1);
        assertThat(jdbc.queryForObject("SELECT raw_body FROM consumer_inbox_delivery WHERE event_id=?", byte[].class,
                UUID.fromString(root.path("eventId").asText()))).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT reference_id FROM attached_result", UUID.class)).isEqualTo(prescriptionId);
        assertThat(jdbc.queryForObject("SELECT operation_key FROM consumer_inbox_operation", String.class))
                .isEqualTo("clinical:prescription:v1:" + root.path("payload").path("dispenseId").asText());
        // A known committed delivery remains a no-op even when the record subsequently completes.
        completeRecord();
        deliver(original);
        deliver(newDelivery(root));
        assertCounts(3, 1, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"patientId", "departmentId", "careEpisodeId", "recordId"})
    void wrongRelationshipRollsBackClaimsAndAttachment(String field) throws Exception {
        ((ObjectNode) root.path("payload")).put(field, UUID.randomUUID().toString());
        assertThatThrownBy(() -> deliver(mapper.writeValueAsBytes(root)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertCounts(0, 0, 0);
    }

    @Test
    void delayedFillCanAppendToCompletedOutpatientRecordWithoutReopeningIt() {
        completeRecord();
        var completedAt = jdbc.queryForObject("SELECT completed_at FROM medical_record WHERE record_id=?", java.sql.Timestamp.class, recordId);
        deliver(original);
        deliver(original);
        assertCounts(1, 1, 1);
        assertThat(jdbc.queryForObject("SELECT status FROM medical_record WHERE record_id=?", String.class, recordId))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT disposition FROM medical_record WHERE record_id=?", String.class, recordId))
                .isEqualTo("OUTPATIENT_FOLLOW_UP");
        assertThat(count("diagnosis")).isOne();
        assertThat(jdbc.queryForObject("SELECT completed_at FROM medical_record WHERE record_id=?", java.sql.Timestamp.class, recordId))
                .isEqualTo(completedAt);
    }

    @Test
    void deferredDispositionCannotReceiveNewOutpatientFill() {
        completeRecord();
        jdbc.update("UPDATE medical_record SET disposition='ADMISSION' WHERE record_id=?", recordId);
        assertThatThrownBy(() -> deliver(original)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertCounts(0, 0, 0);
    }

    @Test
    void decimalSnapshotConflictCannotHideBehindFloatingPointRounding() throws Exception {
        String first = new String(original, java.nio.charset.StandardCharsets.UTF_8)
                .replace("2000.00", "999999999999999.99");
        deliver(first.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String changed = first.replace(root.path("eventId").asText(), UUID.randomUUID().toString())
                .replace("999999999999999.99", "999999999999999.98");
        assertThatThrownBy(() -> deliver(changed.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertCounts(1, 1, 1);
    }

    @Test
    void sameDeliveryDifferentBytesOrSameDispenseDifferentSnapshotIsConflict() throws Exception {
        deliver(original);
        root.put("correlationId", "changed-same-delivery");
        assertThatThrownBy(() -> deliver(mapper.writeValueAsBytes(root)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        root.put("eventId", UUID.randomUUID().toString());
        ((ObjectNode) root.path("payload")).put("totalAmount", 2001);
        assertThatThrownBy(() -> deliver(mapper.writeValueAsBytes(root)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertCounts(1, 1, 1);
    }

    @Test
    void anotherDispenseCannotReusePrescriptionOrHistoricalUnfingerprintedEffect() throws Exception {
        deliver(original);
        ((ObjectNode) root.path("payload")).put("dispenseId", UUID.randomUUID().toString());
        assertThatThrownBy(() -> deliver(newDelivery(root))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertCounts(1, 1, 1);
        jdbc.execute("TRUNCATE consumer_inbox_operation,consumer_inbox_delivery");
        assertThatThrownBy(() -> deliver(original)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertCounts(0, 0, 1);
    }

    @Test
    void failureAfterActualAttachmentRollsBackWholeTransactionAndRetrySucceeds() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            consumer.consume(message(original));
            assertThat(count("attached_result")).isOne();
            throw new IllegalStateException("fault after actual effect");
        })).isInstanceOf(IllegalStateException.class);
        assertCounts(0, 0, 0);
        deliver(original);
        assertCounts(1, 1, 1);
    }

    @Test
    void concurrentSameDispenseNewEventIdsApplyOneEffect() throws Exception {
        byte[] second = newDelivery(root);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> concurrentDeliver(original, ready, start));
            var b = pool.submit(() -> concurrentDeliver(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        } finally { start.countDown(); }
        assertCounts(2, 1, 1);
    }

    @Test
    void originalBytesThroughRabbitReplayOnceAndConflictRetainedOnDurableDlq() throws Exception {
        var connection = new CachingConnectionFactory(MQ.getHost(), MQ.getAmqpPort());
        connection.setUsername(MQ.getAdminUsername());
        connection.setPassword(MQ.getAdminPassword());
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);
        var listener = new SimpleMessageListenerContainer(connection);
        try {
            var admin = new RabbitAdmin(connection);
            String queue = "clinical.rx-v1." + UUID.randomUUID();
            String dlq = queue + ".dlq";
            admin.declareExchange(new TopicExchange("mediflow.events", true, false));
            admin.declareExchange(new TopicExchange("mediflow.events.dlx", true, false));
            admin.declareQueue(QueueBuilder.durable(queue).deadLetterExchange("mediflow.events.dlx")
                    .deadLetterRoutingKey(dlq).build());
            admin.declareQueue(new Queue(dlq, true));
            admin.declareBinding(new Binding(queue, Binding.DestinationType.QUEUE, "mediflow.events", "prescription.filled", null));
            admin.declareBinding(new Binding(dlq, Binding.DestinationType.QUEUE, "mediflow.events.dlx", dlq, null));
            listener.setQueueNames(queue);
            listener.setDefaultRequeueRejected(false);
            listener.setAdviceChain(new RabbitConfig().clinicalListenerRetryInterceptor());
            listener.setMessageListener((org.springframework.amqp.core.MessageListener) consumer::consume);
            listener.start();
            var rabbit = new RabbitTemplate(connection);
            send(rabbit, original);
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertCounts(1, 1, 1));
            send(rabbit, original);
            send(rabbit, newDelivery(root));
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertCounts(2, 1, 1));
            var conflictRoot = root.deepCopy();
            ((ObjectNode) conflictRoot.path("payload")).put("departmentId", UUID.randomUUID().toString());
            byte[] conflict = newDelivery(conflictRoot);
            send(rabbit, conflict);
            Message retained = rabbit.receive(dlq, 10000);
            assertThat(retained).isNotNull();
            assertThat(retained.getBody()).isEqualTo(conflict);
            assertCounts(2, 1, 1);
        } finally {
            listener.stop();
            listener.destroy();
            connection.destroy();
        }
    }

    private void completeRecord() {
        jdbc.update("INSERT INTO diagnosis(diagnosis_id,record_id,diagnosis_name) VALUES (?,?,'fixture diagnosis')", UUID.randomUUID(), recordId);
        jdbc.update("UPDATE medical_record SET status='COMPLETED',disposition='OUTPATIENT_FOLLOW_UP',completed_at=now() WHERE record_id=?", recordId);
    }

    private void send(RabbitTemplate rabbit, byte[] body) {
        rabbit.invoke(ops -> {
            ops.send("mediflow.events", "prescription.filled", message(body));
            ops.waitForConfirmsOrDie(5000);
            return null;
        });
    }

    private void concurrentDeliver(byte[] body, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
            deliver(body);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    private byte[] newDelivery(ObjectNode template) throws Exception {
        return mapper.writeValueAsBytes(template.deepCopy().put("eventId", UUID.randomUUID().toString()));
    }

    private void deliver(byte[] body) { tx.executeWithoutResult(s -> consumer.consume(message(body))); }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private void assertCounts(int deliveries, int operations, int attachments) {
        assertThat(count("consumer_inbox_delivery")).isEqualTo(deliveries);
        assertThat(count("consumer_inbox_operation")).isEqualTo(operations);
        assertThat(count("attached_result")).isEqualTo(attachments);
    }
    private static Message message(byte[] body) {
        var props = new MessageProperties();
        props.setReceivedRoutingKey("prescription.filled");
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
        return new Message(body, props);
    }
}
