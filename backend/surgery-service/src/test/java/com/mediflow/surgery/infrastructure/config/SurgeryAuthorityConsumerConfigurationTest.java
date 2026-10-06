package com.mediflow.surgery.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.surgery.application.port.in.ApplySurgeryAuthorityInvalidationUseCase;
import com.mediflow.surgery.application.port.in.QuerySurgeryAuthorityInvalidationsUseCase;
import com.mediflow.surgery.application.port.in.QuerySurgeryAuthorityInvalidationsUseCase.Candidate;
import com.mediflow.surgery.application.port.in.ReceiveSurgeryAuthorityChangeUseCase;
import com.mediflow.surgery.application.port.in.RecordSurgeryAuthorityInvalidationRetryUseCase;
import com.mediflow.surgery.application.port.out.SurgeryAuthorityChangeWirePort;
import com.mediflow.surgery.application.port.out.SurgeryClockPort;
import com.mediflow.surgery.infrastructure.messaging.SurgeryAuthorityChangeDecoder;
import com.mediflow.surgery.messaging.consumer.SurgeryAuthorityChangeConsumer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SurgeryAuthorityConsumerConfigurationTest {
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void intakeTopologyAndWorkerRequireAllThreeGates(int flags) {
        runner().withPropertyValues("mediflow.features.surgery.enabled=" + ((flags & 1) != 0),
                "mediflow.surgery.messaging.consumers.enabled=" + ((flags & 2) != 0),
                "mediflow.surgery.messaging.organization-authority.enabled=" + ((flags & 4) != 0))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    if (flags == 7) {
                        assertThat(context).hasSingleBean(SurgeryAuthorityChangeConsumer.class).hasSingleBean(SurgeryAuthorityInvalidationWorker.class);
                        assertThat(context).hasBean("surgeryAuthorityTopology");
                    } else {
                        assertThat(context).doesNotHaveBean(SurgeryAuthorityChangeConsumer.class).doesNotHaveBean(SurgeryAuthorityInvalidationWorker.class);
                        assertThat(context).doesNotHaveBean("surgeryAuthorityTopology");
                    }
                });
    }
    @Test void missingFlagsAndTestProfileStayDisabled() {
        runner().run(context -> assertThat(context).doesNotHaveBean(SurgeryAuthorityChangeConsumer.class));
        runner().withPropertyValues("spring.profiles.active=test", "mediflow.features.surgery.enabled=true",
                "mediflow.surgery.messaging.consumers.enabled=true", "mediflow.surgery.messaging.organization-authority.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(SurgeryAuthorityChangeConsumer.class));
    }
    @Test void workerPersistsRetryThenContinuesHealthyCaseWithoutLoggingPayload() {
        var query = mock(QuerySurgeryAuthorityInvalidationsUseCase.class);
        var apply = mock(ApplySurgeryAuthorityInvalidationUseCase.class);
        var retry = mock(RecordSurgeryAuthorityInvalidationRetryUseCase.class);
        var broken = candidate(); var healthy = candidate();
        when(query.findDue(20)).thenReturn(List.of(broken, healthy));
        when(apply.apply(broken)).thenThrow(new IllegalStateException("sensitive reason must not be logged"));
        new SurgeryAuthorityInvalidationWorker(query, apply, retry, 20).poll();
        var order = inOrder(apply, retry);
        order.verify(apply).apply(broken); order.verify(retry).defer(broken, "IllegalStateException"); order.verify(apply).apply(healthy);
    }
    @Test void failedRetryStillAllowsNextCaseAndFailedQueryHasNoSideEffects() {
        var query = mock(QuerySurgeryAuthorityInvalidationsUseCase.class);
        var apply = mock(ApplySurgeryAuthorityInvalidationUseCase.class);
        var retry = mock(RecordSurgeryAuthorityInvalidationRetryUseCase.class);
        var first = candidate(); var second = candidate();
        when(query.findDue(20)).thenReturn(List.of(first, second));
        when(apply.apply(first)).thenThrow(new IllegalStateException());
        doThrow(new IllegalStateException()).when(retry).defer(first, "IllegalStateException");
        var worker = new SurgeryAuthorityInvalidationWorker(query, apply, retry, 20); worker.poll(); verify(apply).apply(second);
        var unavailable = mock(QuerySurgeryAuthorityInvalidationsUseCase.class);
        var unused = mock(ApplySurgeryAuthorityInvalidationUseCase.class);
        when(unavailable.findDue(20)).thenThrow(new IllegalStateException());
        new SurgeryAuthorityInvalidationWorker(unavailable, unused, retry, 20).poll(); verifyNoInteractions(unused);
    }
    @ParameterizedTest @ValueSource(ints = {-1, 0, 101})
    void rejectsUnboundedBatch(int limit) {
        assertThatThrownBy(() -> new SurgeryAuthorityInvalidationWorker(null, null, null, limit)).isInstanceOf(IllegalArgumentException.class);
    }
    private ApplicationContextRunner runner() {
        var configurer = mock(SimpleRabbitListenerContainerFactoryConfigurer.class);
        doAnswer(call -> {
            var factory = call.getArgument(0, SimpleRabbitListenerContainerFactory.class);
            factory.setConnectionFactory(call.getArgument(1, ConnectionFactory.class)); factory.setAutoStartup(false); return null;
        }).when(configurer).configure(any(), any());
        return new ApplicationContextRunner().withUserConfiguration(SurgeryAuthorityConsumerConfiguration.class)
                .withBean(SimpleRabbitListenerContainerFactoryConfigurer.class, () -> configurer)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .withBean(SurgeryClockPort.class, () -> () -> Instant.parse("2026-10-05T02:01:00Z"))
                .withBean(SurgeryAuthorityChangeWirePort.class, () -> new SurgeryAuthorityChangeDecoder(new ObjectMapper()))
                .withBean(ReceiveSurgeryAuthorityChangeUseCase.class, () -> command -> ReceiveSurgeryAuthorityChangeUseCase.Outcome.APPLIED)
                .withBean(QuerySurgeryAuthorityInvalidationsUseCase.class, () -> limit -> List.of())
                .withBean(ApplySurgeryAuthorityInvalidationUseCase.class, () -> candidate -> false)
                .withBean(RecordSurgeryAuthorityInvalidationRetryUseCase.class, () -> (candidate, code) -> {});
    }
    private Candidate candidate() { return new Candidate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1); }
}
