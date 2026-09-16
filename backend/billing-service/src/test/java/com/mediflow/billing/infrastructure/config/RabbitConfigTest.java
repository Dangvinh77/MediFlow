package com.mediflow.billing.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

/** Verifies bounded retry configuration for Billing consumers. */
class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    void listenerFactory_validConfiguration_isCreated() {
        assertThat(config.rabbitListenerContainerFactory(
                mock(ConnectionFactory.class), new Jackson2JsonMessageConverter(),
                3, 1000, 10000, false)).isNotNull();
    }

    @Test
    void listenerFactory_invalidConfiguration_failsFast() {
        assertThatThrownBy(() -> config.rabbitListenerContainerFactory(
                mock(ConnectionFactory.class), new Jackson2JsonMessageConverter(),
                0, 1000, 10000, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
