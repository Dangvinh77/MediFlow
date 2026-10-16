package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.in.ReadOutpatientReportUseCase;
import com.mediflow.report.application.port.out.OutpatientReportReadPort;
import com.mediflow.report.application.service.OutpatientReportReadService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Local wiring only. Recording managers do not prove PostgreSQL atomicity or host startup. */
class ReportModuleWiringTest {
    private final ReportModulePersistenceConfiguration config = new ReportModulePersistenceConfiguration();

    @Test void noImplicitDefaultOrForeignDataSourceCredentials() {
        assertThatThrownBy(() -> config.reportDataSource(new DataSourceProperties(), 4))
                .isInstanceOf(IllegalStateException.class);
        var properties = properties();
        assertThatThrownBy(() -> config.reportDataSource(properties, 0)).isInstanceOf(IllegalStateException.class);
        properties.setUrl("jdbc:h2:mem:foreign");
        assertThatThrownBy(() -> config.reportDataSource(properties, 4)).isInstanceOf(IllegalStateException.class);
    }

    @Test void poolIsBoundedAndMigrationLookupCannotReadAnotherModulesDefaultLocation() {
        try (var ds = config.reportDataSource(properties(), 4)) {
            assertThat(ds.getPoolName()).isEqualTo("report-store");
            assertThat(ds.getMaximumPoolSize()).isEqualTo(4);
            assertThat(ds.getMinimumIdle()).isZero();
            var flyway = config.reportFlyway(ds);
            assertThat(flyway.getConfiguration().getDataSource()).isSameAs(ds);
            assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
            assertThat(flyway.getConfiguration().getLocations()).extracting(Object::toString)
                    .containsExactly(ReportModulePersistenceConfiguration.MIGRATIONS);
        }
    }

    @Test void repositoriesAndJpaValidationBindOnlyNamedReportStore() {
        var repositories = ReportModulePersistenceConfiguration.class.getAnnotation(EnableJpaRepositories.class);
        assertThat(repositories.basePackages()).containsExactly("com.mediflow.report.infrastructure.persistence.repository");
        assertThat(repositories.entityManagerFactoryRef()).isEqualTo("reportEntityManagerFactory");
        assertThat(repositories.transactionManagerRef()).isEqualTo("reportTransactionManager");
        var factory = config.reportEntityManagerFactory(mock(DataSource.class));
        assertThat(factory.getPersistenceUnitName()).isEqualTo("report");
        assertThat(factory.getJpaPropertyMap()).containsEntry("hibernate.hbm2ddl.auto", "validate");
        assertThat(ReportModulePersistenceConfiguration.class.isAnnotationPresent(Primary.class)).isFalse();
    }

    @Test void annotationSelectsReportManagerAndRollsBackEvenWhenAnotherManagerExists() {
        new ApplicationContextRunner().withUserConfiguration(TransactionProbe.class).run(c -> {
            assertThat(c).hasNotFailed();
            assertThatThrownBy(() -> c.getBean(ReadOutpatientReportUseCase.class)
                    .daily(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1), null))
                    .isInstanceOf(ReportProjectionUnavailableException.class);
            var report = c.getBean("reportTransactionManager", RecordingManager.class);
            var other = c.getBean("otherTransactionManager", RecordingManager.class);
            assertThat(report.begun).isOne();
            assertThat(report.rolledBack).isOne();
            assertThat(report.isolation).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            assertThat(other.begun).isZero();
        });
    }

    @Test void modeGuardRejectsLibraryWithoutOutpatientProfile() {
        var environment = new org.springframework.mock.env.MockEnvironment();
        environment.setActiveProfiles("report-module");
        var guard = ReportModuleConfiguration.reportModuleModeGuard(environment);
        assertThatThrownBy(() -> guard.postProcessBeanFactory(new org.springframework.beans.factory.support.DefaultListableBeanFactory()))
                .isInstanceOf(IllegalStateException.class);
        environment.setActiveProfiles("report-module", "outpatient-mvp");
        assertThatCode(() -> guard.postProcessBeanFactory(new org.springframework.beans.factory.support.DefaultListableBeanFactory()))
                .doesNotThrowAnyException();
    }

    private DataSourceProperties properties() {
        var p = new DataSourceProperties();
        p.setUrl("jdbc:postgresql://127.0.0.1:1/isolated_test_only");
        p.setUsername("isolated_test_only"); p.setPassword("isolated_test_only");
        return p;
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionProbe {
        @Bean RecordingManager reportTransactionManager() { return new RecordingManager(); }
        @Bean RecordingManager otherTransactionManager() { return new RecordingManager(); }
        @Bean ReadOutpatientReportUseCase reader() {
            var store = mock(OutpatientReportReadPort.class);
            when(store.load(any(), any(), any(), anyString(), anyString())).thenReturn(Optional.empty());
            return new OutpatientReportReadService(store, ZoneId.of("Asia/Bangkok"), Clock.systemUTC());
        }
    }

    static final class RecordingManager extends AbstractPlatformTransactionManager {
        int begun, rolledBack, isolation;
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object tx, TransactionDefinition definition) { begun++; isolation = definition.getIsolationLevel(); }
        protected void doCommit(DefaultTransactionStatus status) { }
        protected void doRollback(DefaultTransactionStatus status) { rolledBack++; }
    }
}
