package com.mediflow.billing.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

/** A Lab amendment corrects a result Billing already charged for; Billing never subscribes to it. */
class LabAmendmentChargeTopologyTest {
    @Test
    void amendedResultAddsNoCharge() {
        var keys = new RabbitConfig().billingQueueBindings(new Queue("billing.q"), new TopicExchange("mediflow.events"))
                .getDeclarablesByType(Binding.class).stream().map(Binding::getRoutingKey).toList();
        assertThat(keys).doesNotContain("lab.result.amended").contains("lab.result.created");
    }
}
