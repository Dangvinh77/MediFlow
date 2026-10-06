package com.mediflow.surgery.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.surgery.application.port.in.ReactToSurgeryClearanceUseCase;
import com.mediflow.surgery.application.port.out.*;
import com.mediflow.surgery.infrastructure.messaging.SurgeryClearanceDecoder;
import com.mediflow.surgery.messaging.consumer.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SurgeryClearanceConsumerConfigurationTest {
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7})
    void listenersTopologyAndPendingWorkerRequireBothGatesIndependentOfProducer(int flags) {
        boolean business = (flags & 1) != 0, consumer = (flags & 2) != 0, producer = (flags & 4) != 0;
        var configurer = mock(SimpleRabbitListenerContainerFactoryConfigurer.class);
        doAnswer(call -> {
            var factory = call.getArgument(0,SimpleRabbitListenerContainerFactory.class);
            factory.setConnectionFactory(call.getArgument(1,ConnectionFactory.class));
            factory.setAutoStartup(false);
            return null;
        }).when(configurer).configure(any(),any());
        new ApplicationContextRunner().withUserConfiguration(SurgeryClearanceConsumerConfiguration.class)
                .withBean(SimpleRabbitListenerContainerFactoryConfigurer.class,() -> configurer)
                .withBean(ConnectionFactory.class,() -> mock(ConnectionFactory.class))
                .withBean(SurgeryClockPort.class,() -> () -> Instant.parse("2026-10-05T08:01:00Z"))
                .withBean(SurgeryInboxPort.class,() -> mock(SurgeryInboxPort.class))
                .withBean(ReactToSurgeryClearanceUseCase.class,() -> command -> ReactToSurgeryClearanceUseCase.Outcome.APPLIED)
                .withBean(SurgeryClearanceDecoder.class,() -> new SurgeryClearanceDecoder(new ObjectMapper()))
                .withPropertyValues("mediflow.features.surgery.enabled=" + business,
                        "mediflow.surgery.messaging.consumers.enabled=" + consumer,
                        "mediflow.surgery.messaging.producer.enabled=" + producer)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    if (business && consumer) {
                        assertThat(context).hasSingleBean(SurgeryClearanceConsumer.class);
                        assertThat(context).hasSingleBean(PendingSurgeryClearanceWorker.class);
                        assertThat(context).hasBean("surgeryClearanceTopology");
                    } else {
                        assertThat(context).doesNotHaveBean(SurgeryClearanceConsumer.class);
                        assertThat(context).doesNotHaveBean(PendingSurgeryClearanceWorker.class);
                        assertThat(context).doesNotHaveBean("surgeryClearanceTopology");
                    }
                });
    }
}
