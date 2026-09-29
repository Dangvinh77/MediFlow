package com.mediflow.surgery.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mediflow.surgery.application.port.out.SurgeryEventPublisherPort;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

    private static final String EXCHANGE = "mediflow.events.test";

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
}
