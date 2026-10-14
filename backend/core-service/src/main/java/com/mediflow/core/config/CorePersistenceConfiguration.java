package com.mediflow.core.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

@Configuration(proxyBeanMethods = false)
public class CorePersistenceConfiguration {

    @Bean
    @ConfigurationProperties("mediflow.datasource.organization")
    DataSourceProperties organizationDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    DataSource organizationDataSource(
            @Qualifier("organizationDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @ConditionalOnProperty(name = "mediflow.database.migrate", havingValue = "true", matchIfMissing = true)
    Flyway organizationFlyway(@Qualifier("organizationDataSource") DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/organization-migration").load();
    }

    @Bean
    @Primary
    LocalContainerEntityManagerFactoryBean organizationEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("organizationDataSource") DataSource dataSource,
            @Qualifier("organizationFlyway") ObjectProvider<Flyway> flyway) {
        flyway.ifAvailable(Flyway::migrate);
        return builder.dataSource(dataSource)
                .packages("com.mediflow.organization.infrastructure.persistence.entity")
                .persistenceUnit("organization").build();
    }

    @Bean
    @Primary
    PlatformTransactionManager organizationTransactionManager(
            @Qualifier("organizationEntityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }

    @Bean
    @ConfigurationProperties("mediflow.datasource.patient")
    DataSourceProperties patientDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    DataSource patientDataSource(
            @Qualifier("patientDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @ConditionalOnProperty(name = "mediflow.database.migrate", havingValue = "true", matchIfMissing = true)
    Flyway patientFlyway(@Qualifier("patientDataSource") DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/patient-migration").load();
    }

    @Bean
    LocalContainerEntityManagerFactoryBean patientEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("patientDataSource") DataSource dataSource,
            @Qualifier("patientFlyway") ObjectProvider<Flyway> flyway) {
        flyway.ifAvailable(Flyway::migrate);
        return builder.dataSource(dataSource)
                .packages("com.mediflow.patient.infrastructure.persistence")
                .persistenceUnit("patient").build();
    }

    @Bean
    PlatformTransactionManager patientTransactionManager(
            @Qualifier("patientEntityManagerFactory") EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }
}

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
        basePackages = "com.mediflow.organization.infrastructure.persistence.repository",
        entityManagerFactoryRef = "organizationEntityManagerFactory",
        transactionManagerRef = "organizationTransactionManager")
class OrganizationRepositoriesConfiguration {
}

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
        basePackages = "com.mediflow.patient.infrastructure.persistence",
        entityManagerFactoryRef = "patientEntityManagerFactory",
        transactionManagerRef = "patientTransactionManager")
class PatientRepositoriesConfiguration {
}
