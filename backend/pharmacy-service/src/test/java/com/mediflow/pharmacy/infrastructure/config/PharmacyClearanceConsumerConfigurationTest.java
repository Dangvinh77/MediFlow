package com.mediflow.pharmacy.infrastructure.config;

import com.mediflow.pharmacy.application.port.in.ProjectPrescriptionClearanceUseCase;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearanceWirePort;
import com.mediflow.pharmacy.messaging.consumer.PrescriptionClearanceConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PharmacyClearanceConsumerConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void clearanceConsumerRequiresBothExplicitGates(int flags) {
        new ApplicationContextRunner().withUserConfiguration(PharmacyClearanceConsumerConfiguration.class)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(PrescriptionClearanceWirePort.class, () -> mock(PrescriptionClearanceWirePort.class))
                .withBean(ProjectPrescriptionClearanceUseCase.class, () -> mock(ProjectPrescriptionClearanceUseCase.class))
                .withPropertyValues("mediflow.features.care-finance-v2=" + ((flags & 1) != 0),
                        "mediflow.pharmacy.clearance-consumer.enabled=" + ((flags & 2) != 0),
                        "spring.rabbitmq.listener.simple.auto-startup=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    if (flags == 3) {
                        assertThat(context).hasSingleBean(PrescriptionClearanceConsumer.class);
                        assertThat(context).hasBean("pharmacyClearanceTopology");
                    } else {
                        assertThat(context).doesNotHaveBean(PrescriptionClearanceConsumer.class);
                        assertThat(context).doesNotHaveBean("pharmacyClearanceTopology");
                        assertThat(context).doesNotHaveBean("pharmacyClearanceListenerFactory");
                    }
                });
    }
}
