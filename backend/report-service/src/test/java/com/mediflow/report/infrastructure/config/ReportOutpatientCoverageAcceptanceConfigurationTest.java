package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import com.mediflow.report.web.OutpatientCoverageAcceptanceController;
import com.mediflow.report.infrastructure.persistence.adapter.OutpatientCoverageAcceptancePersistenceAdapter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReportOutpatientCoverageAcceptanceConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings={"default-off","global-off","reader-off","approval-off","wrong-profile"})
    void everyGateMustBeExplicitAndMvpOnly(String missing) {
        var runner=new ApplicationContextRunner().withUserConfiguration(ReportOutpatientCoverageAcceptanceConfiguration.class,
                OutpatientCoverageAcceptanceController.class,OutpatientCoverageAcceptancePersistenceAdapter.class)
                .withInitializer(c->c.getEnvironment().setActiveProfiles(missing.equals("wrong-profile")?"dev":"outpatient-mvp"))
                .withPropertyValues("mediflow.features.care-finance-v2="+!missing.equals("global-off"),
                        "mediflow.report.outpatient-read.enabled="+!missing.equals("reader-off"));
        if(!missing.equals("default-off")) runner=runner.withPropertyValues(
                "mediflow.report.outpatient-coverage-acceptance.enabled="+!missing.equals("approval-off"));
        runner.run(c->{ assertThat(c).hasNotFailed(); assertThat(c).doesNotHaveBean(OutpatientCoverageAcceptanceController.class);
            assertThat(c).doesNotHaveBean(OutpatientCoverageAcceptancePersistenceAdapter.class);
            assertThat(c).doesNotHaveBean("outpatientCoverageAcceptance"); });
    }
}
