package com.mediflow.report.infrastructure.config;

import com.mediflow.report.application.port.in.ApplyOperationalContributionUseCase;
import com.mediflow.report.application.port.in.ProjectAdmissionReportEvidenceUseCase;
import com.mediflow.report.application.port.out.CareFinanceWirePort;
import com.mediflow.report.messaging.consumer.OperationalReportFactConsumer;
import java.time.ZoneId;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReportOperationalConsumerConfigurationTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void intake_requiresBothExplicitGates(int flags) {
        runner().withPropertyValues("mediflow.features.care-finance-v2=" + ((flags & 1) != 0),
                "mediflow.report.operational-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) {
                assertThat(context).hasSingleBean(OperationalReportFactConsumer.class);
                assertThat(context).hasBean("reportOperationalTopology");
                assertThat(context).hasBean("reportOperationalListenerFactory");
            } else {
                assertThat(context).doesNotHaveBean(OperationalReportFactConsumer.class);
                assertThat(context).doesNotHaveBean("reportOperationalTopology");
                assertThat(context).doesNotHaveBean("reportOperationalListenerFactory");
            }
        });
    }

    @Test void intake_absentPropertiesCreatesNoConsumer() {
        runner().run(context -> assertThat(context).doesNotHaveBean(OperationalReportFactConsumer.class));
    }

    @Test void topology_sevenExactOperationalKeysNoFinancialBindingsOrLegacyQueue() {
        var topology = new ReportOperationalConsumerConfiguration().reportOperationalTopology();
        assertThat(topology.getDeclarablesByType(Binding.class).stream()
                .filter(b -> b.getExchange().equals("mediflow.events")).map(Binding::getRoutingKey)
                .collect(Collectors.toSet())).containsExactlyInAnyOrder("medicalrecord.completed", "lab.result.created",
                        "prescription.filled", "surgery.completed", "surgery.cancelled", "admission.started", "admission.closed");
        var queues = topology.getDeclarablesByType(Queue.class);
        assertThat(queues).hasSize(2).allMatch(Queue::isDurable);
        assertThat(queues.stream().map(Queue::getName)).containsExactlyInAnyOrder("report.operational-v2.q", "report.operational-v2.dlq");
        assertThat(queues.stream().filter(q -> q.getName().equals("report.operational-v2.q")).findFirst().orElseThrow()
                .getArguments()).containsEntry("x-dead-letter-routing-key", "report.operational-v2.dlq");
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(ReportOperationalConsumerConfiguration.class)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(CareFinanceWirePort.class, () -> mock(CareFinanceWirePort.class))
                .withBean(ApplyOperationalContributionUseCase.class, () -> mock(ApplyOperationalContributionUseCase.class))
                .withBean(ProjectAdmissionReportEvidenceUseCase.class, () -> mock(ProjectAdmissionReportEvidenceUseCase.class))
                .withBean(ZoneId.class, () -> ZoneId.of("Asia/Bangkok"))
                .withPropertyValues("spring.rabbitmq.listener.simple.auto-startup=false");
    }
}
