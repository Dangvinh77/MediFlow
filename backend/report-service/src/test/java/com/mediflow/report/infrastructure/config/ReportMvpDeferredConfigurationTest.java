package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.report.web.*;
import com.mediflow.report.messaging.consumer.ReportEventConsumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReportMvpDeferredConfigurationTest {
    private static final Class<?>[] DEFERRED = {
        ReportFinalRecognitionConsumerConfiguration.class,
        ReportFinancialReadConfiguration.class,
        ReportFinancialPublicationJsonConfiguration.class,
        ReportOperationalConsumerConfiguration.class,
        ReportOperationalPublicationConfiguration.class,
        ReportOperationalPublicationJsonConfiguration.class,
        ReportSettlementConsumerConfiguration.class,
        FinancialSnapshotReportController.class,
        FinancialRebuildController.class,
        FinancialPublicationController.class,
        FinancialSourceArchiveController.class,
        OperationalPublicationController.class,
        OperationalRebuildController.class,
        OperationalSnapshotReportController.class,
        ReportCapabilityController.class,
        ReportEventConsumer.class,
        ReportCashReceiptConsumerConfiguration.class,
        ReportCashRefundConsumerConfiguration.class
    };

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mvpExcludesFullV2AndMixedCareIntakeDespitePropertyOverrides(boolean global) {
        new ApplicationContextRunner().withUserConfiguration(DEFERRED)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("mvp-outpatient", "dev"))
                .withPropertyValues("mediflow.features.care-finance-v2=" + global,
                        "mediflow.report.financial-read.enabled=true",
                        "mediflow.report.financial-archive.enabled=true",
                        "mediflow.report.financial-publication.enabled=true",
                        "mediflow.report.operational-consumer.enabled=true",
                        "mediflow.report.operational-publication.enabled=true",
                        "mediflow.report.settlement-consumer.enabled=true",
                        "mediflow.report.final-recognition-consumer.enabled=true",
                        "mediflow.report.cash-receipt-consumer.enabled=true",
                        "mediflow.report.cash-refund-consumer.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    for (Class<?> type : DEFERRED) {
                        assertThat(context).doesNotHaveBean(type);
                    }
                    assertThat(context).doesNotHaveBean("reportOperationalTopology");
                    assertThat(context).doesNotHaveBean("reportSettlementTopology");
                });
    }
}
