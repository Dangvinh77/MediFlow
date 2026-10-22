package com.mediflow.core.config;

import com.mediflow.core.CoreServiceApplication;
import com.mediflow.organization.application.port.in.ManageAccountsUseCase;
import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CoreServiceApplication.class, properties = "mediflow.demo-seed.enabled=true")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LocalDemoSeedPostgresTest {
    @Container static final PostgreSQLContainer<?> ORGANIZATION = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final PostgreSQLContainer<?> PATIENT = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("mediflow.datasource.organization.url", ORGANIZATION::getJdbcUrl);
        properties.add("mediflow.datasource.organization.username", ORGANIZATION::getUsername);
        properties.add("mediflow.datasource.organization.password", ORGANIZATION::getPassword);
        properties.add("mediflow.datasource.patient.url", PATIENT::getJdbcUrl);
        properties.add("mediflow.datasource.patient.username", PATIENT::getUsername);
        properties.add("mediflow.datasource.patient.password", PATIENT::getPassword);
        properties.add("spring.rabbitmq.host", RABBIT::getHost);
        properties.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        properties.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        properties.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        properties.add("eureka.client.enabled", () -> false);
        properties.add("mediflow.patient.outbox.scheduling-enabled", () -> false);
        properties.add("mediflow.organization.event-outbox.scheduling-enabled", () -> false);
        properties.add("mediflow.jwt.secret", () -> "local-demo-integration-secret-01234567890123456789");
    }

    @Autowired @Qualifier("organizationDataSource") DataSource organizationSource;
    @Autowired @Qualifier("patientDataSource") DataSource patientSource;
    @Autowired @Qualifier("localDemoSeedRunner") ApplicationRunner runner;
    @Autowired VerifyCredentialsUseCase login;
    @Autowired ManageAccountsUseCase accounts;

    @Test void automaticStartupAndReplayKeepRealDatabaseWritesAndChangedCredentials() throws Exception {
        var org = new JdbcTemplate(organizationSource);
        var patient = new JdbcTemplate(patientSource);
        assertThat(org.queryForObject("select count(*) from department", Integer.class)).isEqualTo(10);
        assertThat(org.queryForObject("select count(*) from staff", Integer.class)).isEqualTo(20);
        assertThat(org.queryForObject("select count(*) from account", Integer.class)).isEqualTo(10);
        assertThat(patient.queryForObject("select count(*) from patient", Integer.class)).isEqualTo(10);
        var admin = login.execute("demo.admin", "MediFlowDemo@2026");
        var patientLogin = login.execute("demo.patient", "MediFlowDemo@2026");
        UUID expectedPatient = patient.queryForObject(
                "select entity_id from local_demo_seed_receipt where seed_key='patient-01'", UUID.class);
        assertThat(patientLogin.patientId()).isEqualTo(expectedPatient);
        accounts.changePassword(admin.accountId(), "MediFlowDemo@2026", "UserChangedPassword@2026");
        // Use a changed natural key to prove replay relies on receipts, not the old identity value.
        org.update("update staff set identity_number='777880000001', full_name='Edited Demo' where identity_number='999880000001'");
        var orgOutbox = org.queryForList("select * from organization_event_outbox order by event_id");
        var patientOutbox = patient.queryForList("select * from patient_event_outbox order by event_id");
        runner.run(new DefaultApplicationArguments());
        assertThat(login.execute("demo.admin", "UserChangedPassword@2026").accountId()).isEqualTo(admin.accountId());
        assertThat(org.queryForObject("select count(*) from staff", Integer.class)).isEqualTo(20);
        assertThat(org.queryForObject("select full_name from staff where identity_number='777880000001'", String.class))
                .isEqualTo("Edited Demo");
        assertThat(org.queryForObject("select count(*) from local_demo_seed_receipt", Integer.class)).isEqualTo(40);
        assertThat(patient.queryForObject("select count(*) from local_demo_seed_receipt", Integer.class)).isEqualTo(10);
        assertThat(org.queryForList("select * from organization_event_outbox order by event_id")).isEqualTo(orgOutbox);
        assertThat(patient.queryForList("select * from patient_event_outbox order by event_id")).isEqualTo(patientOutbox);
    }
}
