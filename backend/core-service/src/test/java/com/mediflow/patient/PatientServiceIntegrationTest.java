package com.mediflow.patient;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.patient.application.dto.request.CreatePatientRequest;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.port.in.CreatePatientUseCase;
import com.mediflow.patient.application.port.in.ReadPatientIdentityUseCase;
import com.mediflow.patient.domain.model.Gender;
import com.mediflow.patient.infrastructure.config.RabbitConfig;
import com.mediflow.patient.infrastructure.persistence.PatientJpaRepository;

/**
 * Runs the Patient read/write and event path against the real Flyway schema and
 * RabbitMQ exchange. Docker is required for this integration gate; a missing
 * Docker runtime must fail the build instead of silently skipping it.
 */
@SpringBootTest(classes = com.mediflow.core.CoreServiceApplication.class)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PatientServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres:16-alpine");

    @Container
    static final PostgreSQLContainer<?> ORGANIZATION_POSTGRES = new PostgreSQLContainer<>(
            "postgres:16-alpine");

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            "rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("mediflow.datasource.patient.url", POSTGRES::getJdbcUrl);
        registry.add("mediflow.datasource.patient.username", POSTGRES::getUsername);
        registry.add("mediflow.datasource.patient.password", POSTGRES::getPassword);
        registry.add("mediflow.datasource.organization.url", ORGANIZATION_POSTGRES::getJdbcUrl);
        registry.add("mediflow.datasource.organization.username", ORGANIZATION_POSTGRES::getUsername);
        registry.add("mediflow.datasource.organization.password", ORGANIZATION_POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("mediflow.patient.outbox.scheduling-enabled", () -> false);
        registry.add("mediflow.jwt.secret", () ->
                "integration-test-secret-that-is-long-enough-for-hs256");
    }

    @Autowired
    private CreatePatientUseCase createPatient;

    @Autowired
    private com.mediflow.patient.application.port.in.UpdatePatientUseCase updatePatient;

    @Autowired
    private ReadPatientIdentityUseCase patientLookup;

    @Autowired
    private PatientJpaRepository patientRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.mediflow.patient.infrastructure.messaging.PatientOutboxDispatcher dispatcher;
    @org.springframework.boot.test.mock.mockito.SpyBean
    private com.mediflow.patient.infrastructure.messaging.PatientOutboxStore outbox;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("patientDataSource")
    private javax.sql.DataSource patientDataSource;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("patientTransactionManager")
    private org.springframework.transaction.PlatformTransactionManager patientTransactions;
    @Autowired
    private org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory;

    @org.junit.jupiter.api.BeforeEach
    void clearOutboxFixture() {
        jdbc().update("DELETE FROM patient_event_outbox");
    }

    private org.springframework.jdbc.core.JdbcTemplate jdbc() {
        return new org.springframework.jdbc.core.JdbcTemplate(patientDataSource);
    }

    private void bindEvents() {
        eventQueue = "patient.integration." + UUID.randomUUID();
        // The outage test closes broker connections; use a durable, non-exclusive consumer queue.
        amqpAdmin.declareQueue(new Queue(eventQueue, true, false, false));
        amqpAdmin.declareBinding(BindingBuilder.bind(new Queue(eventQueue))
                .to(new TopicExchange(RabbitConfig.EXCHANGE)).with("patient.*"));
    }

    private PatientDTO newPatient(String name) {
        return createPatient.create(new CreatePatientRequest(name, LocalDate.of(1990, 1, 2), Gender.M,
                UUID.randomUUID().toString().substring(0, 20), "Address", "0901234567", "audit@example.com", null));
    }

    private com.mediflow.patient.application.dto.request.UpdatePatientRequest correction(PatientDTO patient, String name) {
        return new com.mediflow.patient.application.dto.request.UpdatePatientRequest(name, patient.ngaySinh(),
                patient.gioiTinh(), "New address", patient.soDienThoai(), patient.email(), null, null);
    }

    private com.mediflow.patient.infrastructure.messaging.PatientOutboxDispatcher restartedWorker(int attempts) {
        var rabbit = new RabbitTemplate(connectionFactory);
        rabbit.setMandatory(true);
        return new com.mediflow.patient.infrastructure.messaging.PatientOutboxDispatcher(outbox, rabbit,
                new org.springframework.transaction.support.TransactionTemplate(patientTransactions), attempts);
    }

    @Test
    void createAndUpdateSurviveBrokerOutageAndWorkerRestartWithOriginalPayloads() throws Exception {
        bindEvents();
        assertThat(RABBITMQ.execInContainer("rabbitmqctl", "stop_app").getExitCode()).isZero();
        java.util.List<java.util.Map<String, Object>> originals;
        try {
            var patient = newPatient("Outage create");
            updatePatient.update(patient.maBenhNhan(), correction(patient, "Outage update"));
            assertThat(patientRepository.findById(patient.maBenhNhan()).orElseThrow().getFullName())
                    .isEqualTo("Outage update");
            originals = jdbc().queryForList("SELECT event_id,payload,correlation_id,routing_key FROM patient_event_outbox");
            assertThat(originals).hasSize(2);
            dispatcher.dispatch();
            assertThat(jdbc().queryForObject("SELECT count(*) FROM patient_event_outbox WHERE status='PENDING' AND attempts=1", Integer.class))
                    .isEqualTo(2);
        } finally {
            assertThat(RABBITMQ.execInContainer("rabbitmqctl", "start_app").getExitCode()).isZero();
        }
        jdbc().update("UPDATE patient_event_outbox SET next_attempt_at=CURRENT_TIMESTAMP");
        restartedWorker(8).dispatch();
        for (int i = 0; i < 2; i++) {
            Message delivered = rabbitTemplate.receive(eventQueue, 5_000);
            assertThat(delivered).isNotNull();
            var id = delivered.getMessageProperties().getMessageId();
            var original = originals.stream().filter(row -> row.get("event_id").toString().equals(id)).findFirst().orElseThrow();
            assertThat(delivered.getBody()).isEqualTo(original.get("payload").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(delivered.getMessageProperties().getCorrelationId()).isEqualTo(original.get("correlation_id"));
            assertThat(delivered.getMessageProperties().getReceivedRoutingKey()).isEqualTo(original.get("routing_key"));
        }
        assertThat(jdbc().queryForObject("SELECT count(*) FROM patient_event_outbox WHERE status='PUBLISHED'", Integer.class)).isEqualTo(2);
    }

    @Test
    void failureAfterBrokerConfirmReplaysSameBytesAndEventIdAfterWorkerRestart() {
        bindEvents();
        newPatient("Replay patient");
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated termination before mark"))
                .doCallRealMethod().when(outbox).markPublished(org.mockito.ArgumentMatchers.any());
        dispatcher.dispatch();
        Message first = rabbitTemplate.receive(eventQueue, 5_000);
        assertThat(first).isNotNull();
        assertThat(jdbc().queryForObject("SELECT status FROM patient_event_outbox", String.class)).isEqualTo("PENDING");
        restartedWorker(8).dispatch();
        Message replay = rabbitTemplate.receive(eventQueue, 5_000);
        assertThat(replay).isNotNull();
        assertThat(replay.getBody()).isEqualTo(first.getBody());
        assertThat(replay.getMessageProperties().getMessageId()).isEqualTo(first.getMessageProperties().getMessageId());
        assertThat(replay.getMessageProperties().getCorrelationId()).isEqualTo(first.getMessageProperties().getCorrelationId());
        assertThat(jdbc().queryForObject("SELECT status FROM patient_event_outbox", String.class)).isEqualTo("PUBLISHED");
    }

    @Test
    void outboxPersistenceFailureRollsBackPatientCreateAndUpdate() {
        var existing = newPatient("Before rollback");
        jdbc().update("DELETE FROM patient_event_outbox");
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("outbox storage failure")).when(outbox).append(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        long before = patientRepository.count();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> newPatient("Must roll back"))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(patientRepository.count()).isEqualTo(before);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> updatePatient.update(existing.maBenhNhan(), correction(existing, "Must roll back")))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(patientRepository.findById(existing.maBenhNhan()).orElseThrow().getFullName()).isEqualTo("Before rollback");
        assertThat(jdbc().queryForObject("SELECT count(*) FROM patient_event_outbox", Integer.class)).isZero();
    }

    @Test
    void mandatoryReturnQuarantinesAfterBoundedRetriesAndRetainsPayload() {
        // Use an unbound routing key: broker ack alone must not mark success.
        newPatient("Unroutable patient");
        jdbc().update("UPDATE patient_event_outbox SET routing_key='patient.audit.unroutable'");
        String payload = jdbc().queryForObject("SELECT payload FROM patient_event_outbox", String.class);
        var worker = restartedWorker(2);
        worker.dispatch();
        assertThat(jdbc().queryForObject("SELECT status FROM patient_event_outbox", String.class)).isEqualTo("PENDING");
        jdbc().update("UPDATE patient_event_outbox SET next_attempt_at=CURRENT_TIMESTAMP");
        worker.dispatch();
        assertThat(jdbc().queryForObject("SELECT status FROM patient_event_outbox", String.class)).isEqualTo("QUARANTINED");
        assertThat(jdbc().queryForObject("SELECT attempts FROM patient_event_outbox", Integer.class)).isEqualTo(2);
        assertThat(jdbc().queryForObject("SELECT payload FROM patient_event_outbox", String.class)).isEqualTo(payload);
        worker.dispatch();
        assertThat(jdbc().queryForObject("SELECT attempts FROM patient_event_outbox", Integer.class)).isEqualTo(2);
    }

    private String eventQueue;

    @AfterEach
    void removeEventQueue() {
        if (eventQueue != null) {
            amqpAdmin.deleteQueue(eventQueue);
            eventQueue = null;
        }
    }

    @Test
    void concurrentCorrectionsCannotAssignOneIdentityToTwoPatients() throws Exception {
        String prefix = UUID.randomUUID().toString().substring(0, 8);
        PatientDTO first = createPatient.create(new CreatePatientRequest("Race A", LocalDate.of(1990, 1, 1),
                Gender.M, prefix + "-a", null, null, null, null));
        PatientDTO second = createPatient.create(new CreatePatientRequest("Race B", LocalDate.of(1990, 1, 1),
                Gender.F, prefix + "-b", null, null, null, null));
        var start = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (PatientDTO patient : java.util.List.of(first, second)) {
                results.add(pool.submit(() -> {
                    start.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    try {
                        updatePatient.update(patient.maBenhNhan(),
                                new com.mediflow.patient.application.dto.request.UpdatePatientRequest(
                                        patient.hoTen(), patient.ngaySinh(), patient.gioiTinh(),
                                        null, null, null, null, prefix + "-shared"));
                        return true;
                    } catch (com.mediflow.common.exception.DuplicateResourceException
                             | org.springframework.dao.DataIntegrityViolationException expected) {
                        return false;
                    }
                }));
            }
            int successes = 0;
            for (var result : results) {
                if (result.get(30, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            }
            assertThat(successes).isEqualTo(1);
        }
        var identities = java.util.List.of(
                patientRepository.findById(first.maBenhNhan()).orElseThrow().getIdentityNumber(),
                patientRepository.findById(second.maBenhNhan()).orElseThrow().getIdentityNumber());
        assertThat(identities.stream().filter((prefix + "-shared")::equals).count()).isEqualTo(1);
        assertThat(identities).containsAnyOf(prefix + "-a", prefix + "-b");
    }

    @Test
    void correctsIdentityInPostgresAndRejectsAnotherPatientsNumber() {
        String prefix = UUID.randomUUID().toString().substring(0, 8);
        PatientDTO first = createPatient.create(new CreatePatientRequest("Identity A", LocalDate.of(1990, 1, 1),
                Gender.M, prefix + "-old", null, null, null, null));
        PatientDTO second = createPatient.create(new CreatePatientRequest("Identity B", LocalDate.of(1990, 1, 1),
                Gender.F, prefix + "-other", null, null, null, null));
        var correction = new com.mediflow.patient.application.dto.request.UpdatePatientRequest(
                first.hoTen(), first.ngaySinh(), first.gioiTinh(), null, null, null, null, prefix + "-new");
        var persistedCreatedAt = patientRepository.findById(first.maBenhNhan()).orElseThrow().getCreatedAt();

        PatientDTO updated = updatePatient.update(first.maBenhNhan(), correction);
        assertThat(updated.maBenhNhan()).isEqualTo(first.maBenhNhan());
        assertThat(updated.createdAt()).isEqualTo(persistedCreatedAt);
        assertThat(patientRepository.findById(first.maBenhNhan()).orElseThrow().getIdentityNumber())
                .isEqualTo(prefix + "-new");
        assertThat(patientRepository.existsByIdentityNumber(prefix + "-old")).isFalse();
        assertThat(updatePatient.update(first.maBenhNhan(), correction).soCmnd()).isEqualTo(prefix + "-new");
        assertThat(updatePatient.update(first.maBenhNhan(),
                new com.mediflow.patient.application.dto.request.UpdatePatientRequest(
                        first.hoTen(), first.ngaySinh(), first.gioiTinh(), null, null, null, null, null)).soCmnd())
                .isEqualTo(prefix + "-new");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> updatePatient.update(first.maBenhNhan(),
                new com.mediflow.patient.application.dto.request.UpdatePatientRequest(
                        "Rejected name", first.ngaySinh(), first.gioiTinh(), null, null, null, null, second.soCmnd())))
                .isInstanceOf(com.mediflow.common.exception.DuplicateResourceException.class);
        var persisted = patientRepository.findById(first.maBenhNhan()).orElseThrow();
        assertThat(persisted.getIdentityNumber()).isEqualTo(prefix + "-new");
        assertThat(persisted.getFullName()).isEqualTo(first.hoTen());
    }

    @Test
    void createsPatientPersistsLookupIdentityAndPublishesNotificationCompatibleEvent()
            throws Exception {
        eventQueue = "patient.integration." + UUID.randomUUID();
        amqpAdmin.declareQueue(new Queue(eventQueue, false, true, true));
        amqpAdmin.declareBinding(BindingBuilder.bind(new Queue(eventQueue))
                .to(new TopicExchange(RabbitConfig.EXCHANGE))
                .with(RabbitConfig.RK_PATIENT_CREATED));

        PatientDTO created = createPatient.create(new CreatePatientRequest(
                "Integration Patient", LocalDate.of(1990, 1, 2), Gender.M,
                "INT-001", "Integration address", "0901234567",
                "integration.patient@example.com", "01-12345678-9"));

        assertThat(patientRepository.findById(created.maBenhNhan())).isPresent();
        assertThat(patientLookup.exists(created.maBenhNhan()))
                .satisfies(result -> {
                    assertThat(result.exists()).isTrue();
                    assertThat(result.patientId()).isEqualTo(created.maBenhNhan());
                });

        UUID missingId = UUID.randomUUID();
        assertThat(patientLookup.exists(missingId))
                .satisfies(result -> {
                    assertThat(result.exists()).isFalse();
                    assertThat(result.patientId()).isEqualTo(missingId);
                });

        dispatcher.dispatch();
        Message message = rabbitTemplate.receive(eventQueue, 5_000);
        assertThat(message).as("patient.created message").isNotNull();
        Map<?, ?> event = objectMapper.readValue(message.getBody(), Map.class);
        assertThat(event.get("eventId")).isNotNull();
        assertThat(event.get("occurredAt")).isNotNull();
        assertThat(event.get("correlationId")).isNotNull();
        assertThat(event.get("patientId")).isNotNull();
        assertThat(event.get("patientId")).isEqualTo(created.maBenhNhan().toString());
        assertThat(event.get("hoTen")).isEqualTo("Integration Patient");
        assertThat(event.get("email")).isEqualTo("integration.patient@example.com");
        assertThat(event.get("sdt")).isEqualTo("0901234567");
    }
}
