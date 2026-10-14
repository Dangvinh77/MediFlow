package com.mediflow.clinical.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import com.mediflow.clinical.infrastructure.messaging.ClinicalOutboxDispatcher;
import com.mediflow.clinical.infrastructure.persistence.repository.ClinicalOutboxJpaRepository;

class ClinicalOutboxSchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OutboxRuntimeConfiguration.class)
            .withPropertyValues("mediflow.clinical.outbox-poll-interval=3600000")
            .withInitializer(context -> context.getBeanFactory()
                    .setConversionService(ApplicationConversionService.getSharedInstance()));

    @Test
    void enablesTheSingleSchedulerWhenDurableEventsAreOnWithoutCareFinance() {
        contextRunner.withPropertyValues(
                "mediflow.clinical.durable-events.enabled=true",
                "mediflow.features.care-finance-v2.enabled=false",
                "mediflow.features.outpatient-mvp.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ClinicalOutboxDispatcher.class);
                    assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
                });
    }

    @Test
    void activeMvpProfileKeepsSchedulerOnDespiteExplicitFalseFlags() {
        contextRunner.withPropertyValues(
                "mediflow.clinical.durable-events.enabled=false",
                "mediflow.features.care-finance-v2.enabled=false",
                "mediflow.features.outpatient-mvp.enabled=false")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("outpatient-mvp"))
                .run(context -> {
                    assertThat(context).hasSingleBean(ClinicalOutboxDispatcher.class);
                    assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
                });
    }

    @Test
    void leavesSchedulingOffWhenEveryActivationModeIsDisabled() {
        contextRunner.withPropertyValues(
                "mediflow.clinical.durable-events.enabled=false",
                "mediflow.features.care-finance-v2.enabled=false",
                "mediflow.features.outpatient-mvp.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ClinicalOutboxDispatcher.class);
                    assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({ClinicalOutboxDispatcher.class, ClinicalOutboxSchedulingConfiguration.class,
            ClinicalApplicationModeConfiguration.class})
    static class OutboxRuntimeConfiguration {
        @Bean
        RabbitTemplate rabbitTemplate() {
            return mock(RabbitTemplate.class);
        }

        @Bean
        ClinicalOutboxJpaRepository clinicalOutboxJpaRepository() {
            return mock(ClinicalOutboxJpaRepository.class);
        }
    }
}
