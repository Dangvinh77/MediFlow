package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.mediflow.report.application.port.in.ReadOperationalSnapshotUseCase;
import com.mediflow.report.web.OperationalSnapshotReportController;

class OperationalSnapshotFeatureGateTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(ReadOperationalSnapshotUseCase.class, () -> mock(ReadOperationalSnapshotUseCase.class))
            .withUserConfiguration(OperationalSnapshotReportController.class);
    @Test void api_missingOrFalseFlag_doesNotExist() {
        context.run(c -> assertThat(c).doesNotHaveBean(OperationalSnapshotReportController.class));
        context.withPropertyValues("mediflow.features.care-finance-v2=false")
                .run(c -> assertThat(c).doesNotHaveBean(OperationalSnapshotReportController.class));
    }
    @Test void api_explicitFlag_createsControllerWithoutClaimingDataAvailability() {
        context.withPropertyValues("mediflow.features.care-finance-v2=true")
                .run(c -> assertThat(c).hasSingleBean(OperationalSnapshotReportController.class));
    }
}
