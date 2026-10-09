package com.mediflow.pharmacy.infrastructure.config;

import com.mediflow.pharmacy.application.port.in.ProjectAdmissionLifecycleUseCase;
import com.mediflow.pharmacy.application.port.out.AdmissionLifecycleWirePort;
import com.mediflow.pharmacy.messaging.consumer.AdmissionLifecycleConsumer;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PharmacyAdmissionConsumerConfigurationTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void admissionIntake_requiresBothExplicitGates(int flags) {
        runner().withPropertyValues("mediflow.features.care-finance-v2=" + ((flags & 1) != 0),
                "mediflow.pharmacy.admission-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) {
                assertThat(context).hasSingleBean(AdmissionLifecycleConsumer.class);
                assertThat(context).hasBean("pharmacyAdmissionTopology");
                assertThat(context).hasBean("pharmacyAdmissionListenerFactory");
            } else {
                assertThat(context).doesNotHaveBean(AdmissionLifecycleConsumer.class);
                assertThat(context).doesNotHaveBean("pharmacyAdmissionTopology");
                assertThat(context).doesNotHaveBean("pharmacyAdmissionListenerFactory");
            }
        });
    }

    @Test
    void admissionIntake_absentPropertiesRegistersNoQueueOrListener() {
        runner().run(context -> assertThat(context).doesNotHaveBean(AdmissionLifecycleConsumer.class));
    }

    @Test
    void topology_bindsOnlyApprovedLifecycleFactsAndOwnDurableDlq() {
        Declarables topology = new PharmacyAdmissionConsumerConfiguration().pharmacyAdmissionTopology();
        var bindings = topology.getDeclarablesByType(Binding.class);
        Set<String> eventKeys = bindings.stream().filter(b -> b.getExchange().equals("mediflow.events"))
                .map(Binding::getRoutingKey).collect(Collectors.toSet());
        assertThat(eventKeys).containsExactlyInAnyOrder("admission.started", "discharge.medically.approved", "admission.closed");
        var queues = topology.getDeclarablesByType(Queue.class);
        assertThat(queues).allMatch(Queue::isDurable);
        assertThat(queues.stream().filter(q -> q.getName().equals("pharmacy.admission-lifecycle.q")).findFirst().orElseThrow()
                .getArguments()).containsEntry("x-dead-letter-routing-key", "pharmacy.admission-lifecycle.dlq");
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(PharmacyAdmissionConsumerConfiguration.class)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(AdmissionLifecycleWirePort.class, () -> mock(AdmissionLifecycleWirePort.class))
                .withBean(ProjectAdmissionLifecycleUseCase.class, () -> mock(ProjectAdmissionLifecycleUseCase.class))
                .withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false");
    }
}
