package com.mediflow.lab.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    void eventsExchange_isDurableTopicWithCanonicalName() {
        TopicExchange exchange = config.eventsExchange();

        assertThat(exchange.getName()).isEqualTo(RabbitConfig.EVENTS_EXCHANGE);
        assertThat(exchange.isDurable()).isTrue();
        assertThat(exchange.isAutoDelete()).isFalse();
    }

    @Test
    void deadLetterExchange_isDurableTopicWithCanonicalName() {
        TopicExchange exchange = config.deadLetterExchange();

        assertThat(exchange.getName()).isEqualTo(RabbitConfig.DEAD_LETTER_EXCHANGE);
        assertThat(exchange.isDurable()).isTrue();
        assertThat(exchange.isAutoDelete()).isFalse();
    }

    @Test
    void labQueue_isDurableAndDeadLettersToLabDlqRoute() {
        Queue queue = config.labQueue();

        assertThat(queue.getName()).isEqualTo(RabbitConfig.LAB_QUEUE);
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isExclusive()).isFalse();
        assertThat(queue.isAutoDelete()).isFalse();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitConfig.DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitConfig.LAB_DEAD_LETTER_KEY);
    }

    @Test
    void labDeadLetterQueue_isDurable() {
        Queue queue = config.labDeadLetterQueue();

        assertThat(queue.getName()).isEqualTo(RabbitConfig.LAB_DEAD_LETTER_QUEUE);
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.isExclusive()).isFalse();
        assertThat(queue.isAutoDelete()).isFalse();
    }

    @Test
    void medicalRecordCreatedBinding_targetsLabQueueAndCanonicalRoute() {
        Binding binding = config.medicalRecordCreatedBinding(config.labQueue(), config.eventsExchange());

        assertThat(binding.getDestination()).isEqualTo(RabbitConfig.LAB_QUEUE);
        assertThat(binding.getDestinationType()).isEqualTo(Binding.DestinationType.QUEUE);
        assertThat(binding.getExchange()).isEqualTo(RabbitConfig.EVENTS_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitConfig.MEDICAL_RECORD_CREATED);
    }

    @Test
    void paymentCompletedBinding_targetsLabQueueAndCanonicalRoute() {
        Binding binding = config.paymentCompletedBinding(config.labQueue(), config.eventsExchange());

        assertThat(binding.getDestination()).isEqualTo(RabbitConfig.LAB_QUEUE);
        assertThat(binding.getExchange()).isEqualTo(RabbitConfig.EVENTS_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitConfig.PAYMENT_COMPLETED);
    }

    @Test
    void labDeadLetterBinding_targetsDlqAndCanonicalRoute() {
        Binding binding = config.labDeadLetterBinding(config.labDeadLetterQueue(), config.deadLetterExchange());

        assertThat(binding.getDestination()).isEqualTo(RabbitConfig.LAB_DEAD_LETTER_QUEUE);
        assertThat(binding.getDestinationType()).isEqualTo(Binding.DestinationType.QUEUE);
        assertThat(binding.getExchange()).isEqualTo(RabbitConfig.DEAD_LETTER_EXCHANGE);
        assertThat(binding.getRoutingKey()).isEqualTo(RabbitConfig.LAB_DEAD_LETTER_KEY);
    }

    @Test
    void jsonMessageConverter_isJacksonConverter() {
        assertThat(config.rabbitJsonMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    void listenerPoisonMessages_areConfiguredToAvoidDefaultRequeue() throws Exception {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("lab-application", new ClassPathResource("application.yml"))
                .stream()
                .findFirst()
                .orElseThrow();

        assertThat(properties.getProperty("spring.rabbitmq.listener.simple.default-requeue-rejected"))
                .isEqualTo(false);
    }
}
