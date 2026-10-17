package com.mediflow.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.notification.application.port.in.ReactToPrescriptionNoticeUseCase;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.NotificationSourcePort;
import com.mediflow.notification.application.port.out.SurgeryPaymentNoticeWirePort;
import com.mediflow.notification.infrastructure.config.PrescriptionNoticeConfiguration;
import com.mediflow.notification.infrastructure.messaging.PrescriptionNoticeDecoder;
import com.mediflow.notification.infrastructure.messaging.SurgeryPaymentNoticeDecoder;
import com.mediflow.notification.messaging.consumer.PrescriptionNoticeWireHandler;
import com.mediflow.notification.messaging.consumer.PrescriptionPaymentNoticeWireHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class PrescriptionNoticeConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3})
    void bothIndependentGatesAreRequired(int flags) {
        runner().withPropertyValues("mediflow.notification.care-v1.enabled=" + ((flags & 1) != 0),
                "mediflow.notification.outpatient-prescription-consumer.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) {
                assertThat(context).hasSingleBean(PrescriptionNoticeWireHandler.class);
                assertThat(context).hasSingleBean(PrescriptionPaymentNoticeWireHandler.class);
                var topology = context.getBean("outpatientPrescriptionNoticeBindings", Declarables.class);
                assertThat(topology.getDeclarablesByType(Binding.class)).allSatisfy(binding -> {
                    assertThat(binding.getDestination()).isEqualTo("notification.q");
                    assertThat(binding.getExchange()).isEqualTo("mediflow.events");
                });
                assertThat(topology.getDeclarablesByType(Binding.class).stream().map(Binding::getRoutingKey))
                        .containsExactlyInAnyOrder("prescription.created", "prescription.cancelled", "prescription.expired", "prescription.dispense.failed");
            } else {
                assertThat(context).doesNotHaveBean(PrescriptionNoticeWireHandler.class);
                assertThat(context).doesNotHaveBean(PrescriptionPaymentNoticeWireHandler.class);
                assertThat(context).doesNotHaveBean(Declarables.class);
            }
        });
    }

    @Test void defaultsDoNotBindOrCreateRxIntake() {
        runner().run(context -> assertThat(context).doesNotHaveBean(PrescriptionNoticeWireHandler.class));
    }

    @ParameterizedTest @ValueSource(strings = {"prescription.filled", "invoice.created"})
    void historyOutageRollsBackTheWholeClaimAndIntentTransaction(String key) throws Exception {
        var manager = mock(PlatformTransactionManager.class);
        var status = mock(TransactionStatus.class);
        var history = mock(CareNotificationRepositoryPort.class);
        var sources = mock(NotificationSourcePort.class);
        when(manager.getTransaction(any())).thenReturn(status);
        when(history.claim(any(), anyString(), anyString())).thenReturn(true);
        when(sources.claim(anyString(), any(), anyString())).thenReturn(true);
        doThrow(new IllegalStateException("outbox fault")).when(history).deliverInApp(any());
        var command = new PrescriptionNoticeDecoder(new ObjectMapper()).decode("prescription.filled", PrescriptionNoticeContractTest.fixture("filled"));
        runner(manager, history, sources)
                .withPropertyValues("mediflow.notification.care-v1.enabled=true",
                        "mediflow.notification.outpatient-prescription-consumer.enabled=true").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThatThrownBy(() -> {
                        if (key.equals("invoice.created")) context.getBean(PrescriptionPaymentNoticeWireHandler.class)
                                .receive(key, PrescriptionNoticeContractTest.paymentRequestFixture());
                        else context.getBean(ReactToPrescriptionNoticeUseCase.class).receive(command);
                    })
                            .isInstanceOf(IllegalStateException.class).hasMessage("outbox fault");
                    verify(manager).rollback(status);
                    verify(manager, never()).commit(any());
                });
    }

    private ApplicationContextRunner runner() {
        return runner(mock(PlatformTransactionManager.class), mock(CareNotificationRepositoryPort.class),
                mock(NotificationSourcePort.class));
    }

    private ApplicationContextRunner runner(PlatformTransactionManager manager,
            CareNotificationRepositoryPort history, NotificationSourcePort sources) {
        return new ApplicationContextRunner().withUserConfiguration(PrescriptionNoticeConfiguration.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(SurgeryPaymentNoticeWirePort.class, () -> new SurgeryPaymentNoticeDecoder(new ObjectMapper()))
                .withBean("notificationTransactionManager", PlatformTransactionManager.class, () -> manager)
                .withBean(CareNotificationRepositoryPort.class, () -> history)
                .withBean(NotificationSourcePort.class, () -> sources);
    }
}
