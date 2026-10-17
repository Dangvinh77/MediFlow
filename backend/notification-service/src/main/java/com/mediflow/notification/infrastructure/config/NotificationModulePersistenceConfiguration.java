package com.mediflow.notification.infrastructure.config;

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

/** Notification-owned store only. The host supplies explicit credentials, never a default foreign store. */
@Configuration(proxyBeanMethods = false)
@Profile("notification-module")
@EnableJpaRepositories(basePackages = "com.mediflow.notification.infrastructure.persistence.repository",
        entityManagerFactoryRef = "notificationEntityManagerFactory", transactionManagerRef = "notificationTransactionManager")
public class NotificationModulePersistenceConfiguration {
    public static final String MIGRATIONS = "classpath:mediflow/notification/db/migration";

    @Bean
    @ConfigurationProperties("mediflow.notification.datasource")
    DataSourceProperties notificationDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(destroyMethod = "close")
    HikariDataSource notificationDataSource(@Qualifier("notificationDataSourceProperties") DataSourceProperties properties,
            @Value("${mediflow.notification.datasource.maximum-pool-size:4}") int maximumPoolSize) {
        if (properties.getUrl() == null || !properties.getUrl().startsWith("jdbc:postgresql:")
                || properties.getUsername() == null || properties.getUsername().isBlank()
                || properties.getPassword() == null || properties.getPassword().isBlank()
                || maximumPoolSize < 1 || maximumPoolSize > 16) {
            throw new IllegalStateException("Explicit Notification PostgreSQL credentials and bounded pool are required");
        }
        var pool = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        pool.setPoolName("notification-store");
        pool.setMaximumPoolSize(maximumPoolSize);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(3000);
        return pool;
    }

    @Bean(initMethod = "migrate")
    Flyway notificationFlyway(@Qualifier("notificationDataSource") DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource).locations(MIGRATIONS).cleanDisabled(true).load();
    }

    @Bean
    @DependsOn("notificationFlyway")
    LocalContainerEntityManagerFactoryBean notificationEntityManagerFactory(
            @Qualifier("notificationDataSource") DataSource dataSource) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("com.mediflow.notification.infrastructure.persistence");
        factory.setPersistenceUnitName("notification");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.dialect",
                "org.hibernate.dialect.PostgreSQLDialect"));
        return factory;
    }

    @Bean
    JdbcTemplate notificationJdbcTemplate(@Qualifier("notificationDataSource") DataSource dataSource) {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(15);
        return jdbc;
    }

    @Bean
    PlatformTransactionManager notificationTransactionManager(
            @Qualifier("notificationEntityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }
}
