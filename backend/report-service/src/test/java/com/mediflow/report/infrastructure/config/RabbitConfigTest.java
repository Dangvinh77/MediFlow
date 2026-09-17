package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;

/** Verifies the exact V1 report queue topology (five inbound routing keys). */
class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    void reportQueue_bindsExactlyFiveBusinessEvents() {
        Declarables declarables = config.reportQueueBindings(
                config.reportQueue(), config.eventsExchange());

        var routingKeys = declarables.getDeclarables().stream()
                .map(declarable -> ((Binding) declarable).getRoutingKey())
                .collect(Collectors.toSet());

        assertThat(routingKeys).containsExactlyInAnyOrderElementsOf(
                RabbitConfig.SUBSCRIBED_ROUTING_KEYS);
        assertThat(routingKeys).doesNotContain("staff.department.changed");
    }

    @Test
    void reportQueue_routesRejectedMessagesToReportDlq() {
        var queue = config.reportQueue();

        assertThat(queue.getName()).isEqualTo(RabbitConfig.QUEUE);
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", RabbitConfig.DEAD_LETTER_EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitConfig.DEAD_LETTER_ROUTING_KEY);
        assertThat(config.reportDeadLetterQueue().getName()).isEqualTo(RabbitConfig.DLQ);
    }
}
