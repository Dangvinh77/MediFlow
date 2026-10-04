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
 * RabbitMQ exchange. The test is skipped automatically when Docker is not
 * available on the developer machine.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PatientServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres:16-alpine");

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            "rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("mediflow.jwt.secret", () ->
                "integration-test-secret-that-is-long-enough-for-hs256");
    }

    @Autowired
    private CreatePatientUseCase createPatient;

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

    private String eventQueue;

    @AfterEach
    void removeEventQueue() {
        if (eventQueue != null) {
            amqpAdmin.deleteQueue(eventQueue);
            eventQueue = null;
        }
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
