package com.mediflow.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.organization.application.port.in.*;
import com.mediflow.organization.infrastructure.config.OrganizationDemoSeeder;
import com.mediflow.patient.application.port.in.CreatePatientUseCase;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.infrastructure.config.PatientDemoSeeder;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

/** Development composition only: each seeder retains its own datasource/transaction manager. */
@Configuration(proxyBeanMethods = false)
@Profile("(default | local) & !prod & !production & !test & !acceptancefixture & !outpatient-mvp & !mvp-outpatient")
@ConditionalOnProperty(name = "mediflow.demo-seed.enabled", havingValue = "true")
public class LocalDemoSeedConfiguration {
    @Bean
    OrganizationDemoSeeder organizationDemoSeeder(
            @Qualifier("organizationDataSource") DataSource source,
            @Qualifier("organizationTransactionManager") PlatformTransactionManager transactions,
            GetDepartmentUseCase departments, CreateDepartmentUseCase createDepartment,
            GetStaffUseCase staff, CreateStaffUseCase createStaff,
            ManageAccountsUseCase accounts, CreateAccountUseCase createAccount) {
        return new OrganizationDemoSeeder(new JdbcTemplate(source), new TransactionTemplate(transactions),
                departments, createDepartment, staff, createStaff, accounts, createAccount);
    }

    @Bean
    PatientDemoSeeder patientDemoSeeder(@Qualifier("patientDataSource") DataSource source,
            @Qualifier("patientTransactionManager") PlatformTransactionManager transactions,
            GetPatientUseCase patients, CreatePatientUseCase createPatient) {
        return new PatientDemoSeeder(new JdbcTemplate(source), new TransactionTemplate(transactions),
                patients, createPatient);
    }

    @Bean
    ApplicationRunner localDemoSeedRunner(ObjectMapper mapper, OrganizationDemoSeeder organization,
            PatientDemoSeeder patient) {
        return args -> {
            try (var input = new ClassPathResource("demo-seed.json").getInputStream()) {
                var data = mapper.readTree(input);
                var staffIds = organization.seedMasters(data);
                var patientIds = patient.seed(data);
                organization.seedAccounts(data, staffIds, patientIds);
                LoggerFactory.getLogger(LocalDemoSeedConfiguration.class)
                        .info("Local demo seed complete; existing records and credentials preserved");
            }
        };
    }
}
