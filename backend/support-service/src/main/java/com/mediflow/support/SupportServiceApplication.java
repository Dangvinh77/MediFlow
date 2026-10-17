package com.mediflow.support;

import com.mediflow.notification.infrastructure.config.NotificationModuleConfiguration;
import com.mediflow.report.infrastructure.config.ReportModuleConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Import;

/** Composition only: business packages, repositories and transactions remain context-owned. */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class})
@Import({ReportModuleConfiguration.class, NotificationModuleConfiguration.class})
public class SupportServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SupportServiceApplication.class, args);
    }
}
