package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import com.mediflow.report.messaging.consumer.ReportEventConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReportMvpProfileAliasTest {
    @ParameterizedTest
    @ValueSource(strings = {"outpatient-mvp", "mvp-outpatient"})
    void aliasesLoadDefaultOffGatesAndExcludeLegacyOrMixedIntake(String profile) {
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(ReportEventConsumer.class, ReportOperationalConsumerConfiguration.class,
                        ReportCashReceiptConsumerConfiguration.class, ReportCashRefundConsumerConfiguration.class,
                        ReportFinalRecognitionConsumerConfiguration.class)
                .withPropertyValues("spring.profiles.active=" + profile,
                        "mediflow.features.care-finance-v2=true",
                        "mediflow.report.operational-consumer.enabled=true",
                        "mediflow.report.cash-receipt-consumer.enabled=true",
                        "mediflow.report.cash-refund-consumer.enabled=true",
                        "mediflow.report.final-recognition-consumer.enabled=true")
                .run(c -> {
                    assertThat(c).hasNotFailed().doesNotHaveBean(ReportEventConsumer.class)
                            .doesNotHaveBean(ReportOperationalConsumerConfiguration.class)
                            .doesNotHaveBean(ReportCashReceiptConsumerConfiguration.class)
                            .doesNotHaveBean(ReportCashRefundConsumerConfiguration.class)
                            .doesNotHaveBean(ReportFinalRecognitionConsumerConfiguration.class);
                    assertThat(c.getEnvironment().getProperty("mediflow.report.outpatient-consumer.enabled", Boolean.class)).isFalse();
                    assertThat(c.getEnvironment().getProperty("mediflow.report.outpatient-read.enabled", Boolean.class)).isFalse();
                });
    }
}
