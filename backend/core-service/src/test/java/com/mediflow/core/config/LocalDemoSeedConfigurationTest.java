package com.mediflow.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.organization.application.port.in.*;
import com.mediflow.organization.infrastructure.config.OrganizationDemoSeeder;
import com.mediflow.patient.application.port.in.CreatePatientUseCase;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.infrastructure.config.PatientDemoSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocalDemoSeedConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(LocalDemoSeedConfiguration.class);

    @Test void disabledPropertyCreatesNoSeeder() {
        context.withPropertyValues("mediflow.demo-seed.enabled=false")
                .run(it -> assertThat(it).doesNotHaveBean(ApplicationRunner.class));
    }

    @Test void defaultAndLocalProfilesWireBothOwnedStoresAndTheStartupRunner() {
        var wired = context
                .withPropertyValues("mediflow.demo-seed.enabled=true")
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean("organizationDataSource", DataSource.class, () -> mock(DataSource.class))
                .withBean("patientDataSource", DataSource.class, () -> mock(DataSource.class))
                .withBean("organizationTransactionManager", PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean("patientTransactionManager", PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
                .withBean(GetDepartmentUseCase.class, () -> mock(GetDepartmentUseCase.class))
                .withBean(CreateDepartmentUseCase.class, () -> mock(CreateDepartmentUseCase.class))
                .withBean(GetStaffUseCase.class, () -> mock(GetStaffUseCase.class))
                .withBean(CreateStaffUseCase.class, () -> mock(CreateStaffUseCase.class))
                .withBean(ManageAccountsUseCase.class, () -> mock(ManageAccountsUseCase.class))
                .withBean(CreateAccountUseCase.class, () -> mock(CreateAccountUseCase.class))
                .withBean(GetPatientUseCase.class, () -> mock(GetPatientUseCase.class))
                .withBean(CreatePatientUseCase.class, () -> mock(CreatePatientUseCase.class));
        wired.run(it -> {
            assertThat(it).hasSingleBean(OrganizationDemoSeeder.class).hasSingleBean(PatientDemoSeeder.class)
                    .hasSingleBean(ApplicationRunner.class);
        });
        wired.withInitializer(it -> it.getEnvironment().setActiveProfiles("local"))
                .run(it -> assertThat(it).hasSingleBean(ApplicationRunner.class));
    }

    @Test void productionAndAcceptanceProfilesNeverSeedEvenIfEnabled() {
        for (String profile : new String[] {"prod", "production", "outpatient-mvp", "test", "acceptancefixture"}) {
            context.withInitializer(it -> it.getEnvironment().setActiveProfiles(profile))
                    .withPropertyValues("mediflow.demo-seed.enabled=true")
                    .run(it -> assertThat(it).doesNotHaveBean(ApplicationRunner.class));
            context.withInitializer(it -> it.getEnvironment().setActiveProfiles("local", profile))
                    .withPropertyValues("mediflow.demo-seed.enabled=true")
                    .run(it -> assertThat(it).doesNotHaveBean(ApplicationRunner.class));
        }
    }

    @Test void startupOrdersSeparateContextPhasesAndPassesActualPatientIds() throws Exception {
        var organization = mock(OrganizationDemoSeeder.class);
        var patient = mock(PatientDemoSeeder.class);
        var staffIds = Map.of("staff-01", UUID.randomUUID());
        var patientIds = Map.of("patient-01", UUID.randomUUID());
        when(organization.seedMasters(any())).thenReturn(staffIds);
        when(patient.seed(any())).thenReturn(patientIds);
        new LocalDemoSeedConfiguration().localDemoSeedRunner(new ObjectMapper(), organization, patient)
                .run(new DefaultApplicationArguments());
        var order = inOrder(organization, patient);
        order.verify(organization).seedMasters(any());
        order.verify(patient).seed(any());
        order.verify(organization).seedAccounts(any(), eq(staffIds), eq(patientIds));
        order.verifyNoMoreInteractions();
    }
}
