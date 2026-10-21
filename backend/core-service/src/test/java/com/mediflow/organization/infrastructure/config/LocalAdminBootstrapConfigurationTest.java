package com.mediflow.organization.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import com.mediflow.organization.application.port.in.BootstrapAdminUseCase;

class LocalAdminBootstrapConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(LocalAdminBootstrapConfiguration.class)
            .withBean("organizationDataSource", DataSource.class, () -> mock(DataSource.class))
            .withBean("organizationTransactionManager", PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(BootstrapAdminUseCase.class, () -> mock(BootstrapAdminUseCase.class));
    @Test void unavailableOutsideLocalEvenIfEnabled() {
        context.withPropertyValues("mediflow.bootstrap.admin.enabled=true")
                .run(it -> assertThat(it).doesNotHaveBean(ApplicationRunner.class));
    }
    @Test void localProfileStillRequiresExplicitOptIn() {
        context.withInitializer(it -> it.getEnvironment().setActiveProfiles("local"))
                .run(it -> assertThat(it).doesNotHaveBean(ApplicationRunner.class));
    }
    @Test void enabledOnlyWithBothGates() {
        context.withInitializer(it -> it.getEnvironment().setActiveProfiles("local"))
                .withPropertyValues("mediflow.bootstrap.admin.enabled=true")
                .run(it -> assertThat(it).hasSingleBean(ApplicationRunner.class));
    }
}
