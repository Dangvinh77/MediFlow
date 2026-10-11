package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mediflow.report.application.port.in.PublishFinancialSnapshotsUseCase;
import com.mediflow.report.application.port.in.InspectReportCapabilitiesUseCase;
import com.mediflow.report.application.port.in.RebuildFinancialSourcesUseCase;
import com.mediflow.report.web.FinancialPublicationController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReportFinancialPublicationConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(FinancialPublicationController.class,
                    com.mediflow.report.web.FinancialRebuildController.class,
                    com.mediflow.report.web.ReportCapabilityController.class,
                    ReportFinancialPublicationJsonConfiguration.class)
            .withBean(InspectReportCapabilitiesUseCase.class, () -> mock(InspectReportCapabilitiesUseCase.class))
            .withBean(RebuildFinancialSourcesUseCase.class, () -> mock(RebuildFinancialSourcesUseCase.class))
            .withBean(PublishFinancialSnapshotsUseCase.class,
                    () -> mock(PublishFinancialSnapshotsUseCase.class));

    @Test
    void publication_defaultDisabled_doesNotRegisterController() {
        context.run(state -> assertThat(state)
                .doesNotHaveBean(FinancialPublicationController.class)
                .doesNotHaveBean(com.mediflow.report.web.FinancialRebuildController.class)
                .doesNotHaveBean(com.mediflow.report.web.ReportCapabilityController.class)
                .doesNotHaveBean(ReportFinancialPublicationJsonConfiguration.class));
    }

    @Test
    void publication_onlyCareFinanceGate_doesNotRegisterController() {
        context.withPropertyValues("mediflow.features.care-finance-v2=true")
                .run(state -> assertThat(state)
                        .doesNotHaveBean(FinancialPublicationController.class)
                        .doesNotHaveBean(com.mediflow.report.web.FinancialRebuildController.class)
                        .doesNotHaveBean(com.mediflow.report.web.ReportCapabilityController.class)
                        .doesNotHaveBean(ReportFinancialPublicationJsonConfiguration.class));
    }

    @Test
    void publication_onlyPublicationGate_doesNotRegisterController() {
        context.withPropertyValues("mediflow.report.financial-publication.enabled=true")
                .run(state -> assertThat(state)
                        .doesNotHaveBean(FinancialPublicationController.class)
                        .doesNotHaveBean(com.mediflow.report.web.FinancialRebuildController.class)
                        .doesNotHaveBean(com.mediflow.report.web.ReportCapabilityController.class)
                        .doesNotHaveBean(ReportFinancialPublicationJsonConfiguration.class));
    }

    @Test
    void publication_bothExplicitGates_registersController() {
        context.withPropertyValues(
                        "mediflow.features.care-finance-v2=true",
                        "mediflow.report.financial-publication.enabled=true")
                .run(state -> assertThat(state)
                        .hasSingleBean(FinancialPublicationController.class)
                        .hasSingleBean(com.mediflow.report.web.FinancialRebuildController.class)
                        .hasSingleBean(com.mediflow.report.web.ReportCapabilityController.class)
                        .hasSingleBean(ReportFinancialPublicationJsonConfiguration.class));
    }
}
