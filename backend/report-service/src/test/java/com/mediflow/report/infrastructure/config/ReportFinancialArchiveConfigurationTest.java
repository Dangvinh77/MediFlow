package com.mediflow.report.infrastructure.config;

import com.mediflow.report.application.port.in.ManageFinancialSourceArchivesUseCase;
import com.mediflow.report.web.FinancialSourceArchiveController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ReportFinancialArchiveConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(FinancialSourceArchiveController.class)
            .withBean(ManageFinancialSourceArchivesUseCase.class, () -> mock(ManageFinancialSourceArchivesUseCase.class));
    @Test void archive_requiresBothIndependentExplicitGates() {
        context.run(state -> assertThat(state).doesNotHaveBean(FinancialSourceArchiveController.class));
        context.withPropertyValues("mediflow.features.care-finance-v2=true").run(state -> assertThat(state).doesNotHaveBean(FinancialSourceArchiveController.class));
        context.withPropertyValues("mediflow.report.financial-archive.enabled=true").run(state -> assertThat(state).doesNotHaveBean(FinancialSourceArchiveController.class));
        context.withPropertyValues("mediflow.features.care-finance-v2=true","mediflow.report.financial-archive.enabled=true")
                .run(state -> assertThat(state).hasSingleBean(FinancialSourceArchiveController.class));
    }
}
