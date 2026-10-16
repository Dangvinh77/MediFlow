package com.mediflow.report.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/** Report-owned store only. The host supplies explicit credentials, never a default foreign store. */
@Configuration(proxyBeanMethods = false)
@Profile("report-module")
@EnableJpaRepositories(basePackages = "com.mediflow.report.infrastructure.persistence.repository",
        entityManagerFactoryRef = "reportEntityManagerFactory", transactionManagerRef = "reportTransactionManager")
public class ReportModulePersistenceConfiguration {
    public static final String MIGRATIONS = "classpath:mediflow/report/db/migration";

    @Bean
    @ConfigurationProperties("mediflow.report.datasource")
    DataSourceProperties reportDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(destroyMethod = "close")
    HikariDataSource reportDataSource(@Qualifier("reportDataSourceProperties") DataSourceProperties properties,
            @Value("${mediflow.report.datasource.maximum-pool-size:4}") int maximumPoolSize) {
        if (properties.getUrl() == null || !properties.getUrl().startsWith("jdbc:postgresql:")
                || properties.getUsername() == null || properties.getUsername().isBlank()
                || properties.getPassword() == null || properties.getPassword().isBlank()
                || maximumPoolSize < 1 || maximumPoolSize > 16) {
            throw new IllegalStateException("Explicit Report PostgreSQL credentials and bounded pool are required");
        }
        var pool = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        pool.setPoolName("report-store");
        pool.setMaximumPoolSize(maximumPoolSize);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(3000);
        return pool;
    }

    @Bean(initMethod = "migrate")
    Flyway reportFlyway(@Qualifier("reportDataSource") DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations(MIGRATIONS).cleanDisabled(true).load();
    }

    @Bean
    @DependsOn("reportFlyway")
    LocalContainerEntityManagerFactoryBean reportEntityManagerFactory(
            @Qualifier("reportDataSource") DataSource dataSource) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.mediflow.report.infrastructure.persistence");
        factory.setPersistenceUnitName("report");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.dialect",
                "org.hibernate.dialect.PostgreSQLDialect"));
        return factory;
    }

    @Bean
    JdbcTemplate reportJdbcTemplate(@Qualifier("reportDataSource") DataSource dataSource) {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(15);
        return jdbc;
    }

    @Bean
    PlatformTransactionManager reportTransactionManager(
            @Qualifier("reportEntityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }
}
