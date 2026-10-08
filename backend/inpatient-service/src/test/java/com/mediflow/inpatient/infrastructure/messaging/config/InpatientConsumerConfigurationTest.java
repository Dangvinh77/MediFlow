package com.mediflow.inpatient.infrastructure.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class InpatientConsumerConfigurationTest {
    @Test
    void surgeryCaseCreatedBinding_usesCanonicalRoutingKey() {
        InpatientConsumerConfiguration configuration = new InpatientConsumerConfiguration();

        var binding = configuration.surgeryCaseCreatedBinding(
                new Queue(InpatientConsumerConfiguration.INPATIENT_QUEUE),
                new TopicExchange(InpatientConsumerConfiguration.EVENTS_EXCHANGE));

        assertThat(binding.getRoutingKey()).isEqualTo("surgery.case.created");
    }
}
