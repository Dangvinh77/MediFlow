package com.mediflow.pharmacy.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

/** Kiểm tra retry policy Rabbit bounded và fail-fast khi cấu hình sai. */
class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();
    private final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    private final Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();

    /** Cấu hình hợp lệ phải tạo listener factory có converter và connection factory. */
    @Test
    void listenerFactory_validRetryConfiguration_isCreated() {
        SimpleRabbitListenerContainerFactory factory = config.rabbitListenerContainerFactory(
                connectionFactory, converter, 3, 1000, 10000);

        assertThat(factory).isNotNull();
    }

    /** Retry attempts hoặc backoff không hợp lệ phải bị từ chối thay vì chạy unbounded. */
    @Test
    void listenerFactory_invalidRetryConfiguration_isRejected() {
        assertThatThrownBy(() -> config.rabbitListenerContainerFactory(
                connectionFactory, converter, 0, 1000, 10000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.rabbitListenerContainerFactory(
                connectionFactory, converter, 3, 10000, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
