package com.mediflow.report.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import com.mediflow.report.ReportServiceApplication;
import com.mediflow.report.application.port.in.ReadReportUseCase;
import com.mediflow.packaging.ReportDatabaseTestHost;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Two disposable test-owned stores; not Notification implementation or six-app acceptance. */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles({"report-module", "outpatient-mvp"})
@SpringBootTest(classes = ReportDatabaseTestHost.class, properties = {
        "eureka.client.enabled=false", "spring.cloud.discovery.enabled=false",
        "mediflow.jwt.secret=isolated-report-module-test-secret-at-least-32-bytes",
        "spring.rabbitmq.dynamic=false", "spring.rabbitmq.listener.simple.auto-startup=false"})
class ReportModulePostgresTest {
    @Container static final PostgreSQLContainer<?> REPORT = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final PostgreSQLContainer<?> OTHER = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void sources(DynamicPropertyRegistry r) {
        r.add("mediflow.report.datasource.url", REPORT::getJdbcUrl);
        r.add("mediflow.report.datasource.username", REPORT::getUsername);
        r.add("mediflow.report.datasource.password", REPORT::getPassword);
        r.add("test.other.datasource.url", OTHER::getJdbcUrl);
        r.add("test.other.datasource.username", OTHER::getUsername);
        r.add("test.other.datasource.password", OTHER::getPassword);
    }
    @Autowired ApplicationContext context;
    @Autowired ReadReportUseCase reports;
    @Autowired @Qualifier("reportJdbcTemplate") JdbcTemplate report;
    @Autowired @Qualifier("otherJdbcTemplate") JdbcTemplate other;

    @Test void migrationRepositoriesAndTransactionsRemainInReportStoreWithAnotherStorePresent() {
        assertThat(report.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isEqualTo(24);
        assertThat(other.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name='flyway_schema_history'",
                Integer.class)).isZero();
        assertThat(reports.daily(LocalDate.of(2026, 10, 1), null)).isNotNull();
        assertThat(context.getBean("reportTransactionManager")).isNotSameAs(context.getBean("otherTransactionManager"));
        assertThat(context.containsBean("transactionManager")).isFalse();
        assertThat(context.getBeansOfType(ReportServiceApplication.class)).isEmpty();
    }

}
