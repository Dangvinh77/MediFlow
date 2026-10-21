package com.mediflow.organization.infrastructure.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.mediflow.organization.application.port.in.BootstrapAdminUseCase;

/** Explicit local-only bootstrap. No HTTP endpoint and no built-in credentials. */
@Configuration(proxyBeanMethods = false)
@Profile("local")
@ConditionalOnProperty(name = "mediflow.bootstrap.admin.enabled", havingValue = "true")
public class LocalAdminBootstrapConfiguration {
    @Bean
    ApplicationRunner bootstrapAdmin(Environment environment, BootstrapAdminUseCase bootstrap,
            @Qualifier("organizationDataSource") DataSource source,
            @Qualifier("organizationTransactionManager") PlatformTransactionManager transactions) {
        return args -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            // Concurrent local startups must not create two initial administrators.
            new JdbcTemplate(source).execute("select pg_advisory_xact_lock(78324511)");
            bootstrap.execute(
                    environment.getRequiredProperty("mediflow.bootstrap.admin.username"),
                    environment.getRequiredProperty("mediflow.bootstrap.admin.password"),
                    environment.getRequiredProperty("mediflow.bootstrap.admin.identity-number"),
                    environment.getRequiredProperty("mediflow.bootstrap.admin.full-name"),
                    environment.getProperty("mediflow.bootstrap.admin.department-abbreviation", "QTHT"));
        });
    }
}
