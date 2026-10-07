package com.mediflow.surgery.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediflow.surgery.application.port.out.SurgeryEventPublisherPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RabbitSurgeryEventPublisherAdapterTest {

    private static final String EXCHANGE = "mediflow.events";

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-alpine");

    private CachingConnectionFactory connectionFactory;
    private RabbitTemplate rabbitTemplate;
    private RabbitSurgeryEventPublisherAdapter publisher;

    @BeforeEach
    void configureCorrelatedConfirmsAndReturns() {
        connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        connectionFactory.setUsername(RABBIT.getAdminUsername());
        connectionFactory.setPassword(RABBIT.getAdminPassword());
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);
        publisher = new RabbitSurgeryEventPublisherAdapter(rabbitTemplate, EXCHANGE, 5000);

        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate);
        admin.declareExchange(new TopicExchange(EXCHANGE, true, false));
    }

    @AfterEach
    void closeConnectionFactory() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void publish_confirmedDeliveryPreservesExactStoredBytes() {
        String queueName = "surgery-ready-" + UUID.randomUUID();
        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate);
        Queue queue = new Queue(queueName, false, false, true);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange(EXCHANGE, true, false)).with("surgery.ready"));
        byte[] payload = "{ \"eventId\" : \"stored-bytes\", \"version\": 1 }"
                .getBytes(StandardCharsets.UTF_8);

        publisher.publish(new SurgeryEventPublisherPort.OutgoingMessage(
                UUID.randomUUID(), "surgery.ready", 1, "corr-1", payload));

        Message delivered = rabbitTemplate.receive(queueName, 5000);
        assertThat(delivered).isNotNull();
        assertThat(delivered.getBody()).containsExactly(payload);
        assertThat(delivered.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(delivered.getMessageProperties().getCorrelationId()).isEqualTo("corr-1");
        assertThat(delivered.getMessageProperties().getHeaders()).containsEntry("eventVersion", 1);
    }

    @Test
    void publish_unroutableMessageReportsMandatoryReturn() {
        assertThatThrownBy(() -> publisher.publish(new SurgeryEventPublisherPort.OutgoingMessage(
                UUID.randomUUID(), "surgery.no-route", 1, "corr-2", new byte[] {9})))
                .isInstanceOf(SurgeryEventPublishException.class)
                .satisfies(exception -> assertThat(((SurgeryEventPublishException) exception).returned())
                        .isTrue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "surgery.case.created.admission.v1.json", "surgery.case.created.outpatient.v1.json",
            "surgery.completed.admission.v1.json", "surgery.completed.outpatient.v1.json",
            "surgery.completed.admission.planned-difference.v1.json",
            "surgery.completed.outpatient.planned-difference.v1.json",
            "surgery.completed.admission.unknown-price.v1.json",
            "surgery.completed.outpatient.unknown-price.v1.json"
    })
    void publish_billingProducerFixture_preservesRoutingVersionIdentityAndExactBytes(String fixture) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/contracts/surgery-outcomes-v1", fixture));
        var envelope = new ObjectMapper().readTree(bytes);
        String routingKey = envelope.path("eventType").asText();
        assertThat(routingKey).isIn("surgery.case.created", "surgery.completed");
        assertThat(envelope.path("version").intValue()).isEqualTo(1);
        assertThat(envelope.path("producer").asText()).isEqualTo("surgery-service");
        String queueName = "billing-surgery-contract-" + UUID.randomUUID();
        RabbitAdmin admin = new RabbitAdmin(rabbitTemplate);
        // Timed receive creates a short-lived consumer; keep the queue through both deliveries.
        Queue queue = new Queue(queueName, false, false, false);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue).to(new TopicExchange(EXCHANGE, true, false)).with(routingKey));

        var outgoing = new SurgeryEventPublisherPort.OutgoingMessage(
                UUID.fromString(envelope.path("eventId").asText()), routingKey, 1,
                envelope.path("correlationId").asText(), bytes);
        publisher.publish(outgoing);
        publisher.publish(outgoing);

        for (int delivery = 0; delivery < 2; delivery++) {
            Message delivered = rabbitTemplate.receive(queueName, 5000);
            assertThat(delivered).isNotNull();
            assertThat(delivered.getBody()).containsExactly(bytes);
            var properties = delivered.getMessageProperties();
            assertThat(properties.getReceivedExchange()).isEqualTo("mediflow.events");
            assertThat(properties.getReceivedRoutingKey()).isEqualTo(routingKey);
            assertThat(properties.getMessageId()).isEqualTo(outgoing.eventId().toString());
            assertThat(properties.getCorrelationId()).isEqualTo(outgoing.correlationId());
            assertThat(properties.getContentType()).isEqualTo("application/json");
            assertThat(properties.getHeaders()).containsEntry("eventType", routingKey).containsEntry("eventVersion", 1);
        }
        assertThat(rabbitTemplate.receive(queueName, 100)).isNull();
        admin.deleteQueue(queueName);
    }
}
