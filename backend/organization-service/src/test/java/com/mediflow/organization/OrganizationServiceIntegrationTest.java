package com.mediflow.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.organization.application.port.in.CreateDepartmentUseCase;
import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.in.LookupDepartmentUseCase;
import com.mediflow.organization.application.port.in.LookupStaffIdentityUseCase;
import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.mediflow.organization.infrastructure.persistence.repository.StaffJpaRepository;

/**
 * Verifies the real Flyway schema, JPA mappings, transaction boundary, and
 * RabbitMQ wiring together. The test is skipped automatically when Docker is
 * unavailable on a developer machine.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrganizationServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres:16-alpine");

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            "rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("mediflow.jwt.secret", () ->
                "integration-test-secret-that-is-long-enough-for-hs256");
    }

    @Autowired
    private CreateDepartmentUseCase createDepartmentUseCase;

    @Autowired
    private CreateStaffUseCase createStaffUseCase;

    @Autowired
    private CreateAccountUseCase createAccountUseCase;

    @Autowired
    private VerifyCredentialsUseCase verifyCredentialsUseCase;

    @Autowired
    private LookupStaffIdentityUseCase lookupStaffIdentityUseCase;

    @Autowired
    private LookupDepartmentUseCase lookupDepartmentUseCase;

    @Autowired
    private StaffRepository staffStore;

    @Autowired
    private DepartmentRepository departmentStore;

    @Autowired
    private DepartmentJpaRepository departmentRepository;

    @Autowired
    private StaffJpaRepository staffRepository;

    @Test
    void createsDepartmentAndStaffAgainstFlywaySchema() {
        var department = createDepartmentUseCase.execute(
                "Integration Cardiology",
                "INT-CARD",
                DepartmentType.CLINICAL,
                "Building I");

        var staff = createStaffUseCase.execute(
                "Integration Doctor",
                department.getDepartmentId(),
                JobTitle.DOCTOR,
                "Cardiology",
                "INT-MED-001",
                "0901234567",
                "integration.doctor@example.com");

        assertThat(departmentRepository.findById(department.getDepartmentId()))
                .isPresent()
                .get()
                .extracting(entity -> entity.getDepartmentName())
                .isEqualTo("Integration Cardiology");
        assertThat(staffRepository.findById(staff.getStaffId()))
                .isPresent()
                .get()
                .extracting(entity -> entity.getDepartmentId())
                .isEqualTo(department.getDepartmentId());

        // Keep the assertion tied to the UUID contract, not an entity relation.
        assertThat(staff.getStaffId()).isInstanceOf(UUID.class);

        var staffLookup = lookupStaffIdentityUseCase.lookup(staff.getStaffId());
        assertThat(staffLookup.exists()).isTrue();
        assertThat(staffLookup.active()).isTrue();
        assertThat(staffLookup.departmentId()).isEqualTo(department.getDepartmentId());
        assertThat(staffLookup.jobTitle()).isEqualTo(JobTitle.DOCTOR.name());

        var departmentLookup = lookupDepartmentUseCase.lookup(department.getDepartmentId());
        assertThat(departmentLookup.exists()).isTrue();
        assertThat(departmentLookup.active()).isTrue();
        assertThat(departmentLookup.departmentId()).isEqualTo(department.getDepartmentId());
        assertThat(departmentLookup.departmentName()).isEqualTo("Integration Cardiology");

        UUID missingStaffId = UUID.randomUUID();
        assertThat(lookupStaffIdentityUseCase.lookup(missingStaffId))
                .satisfies(result -> {
                    assertThat(result.exists()).isFalse();
                    assertThat(result.active()).isFalse();
                    assertThat(result.departmentId()).isNull();
                });
        UUID missingDepartmentId = UUID.randomUUID();
        assertThat(lookupDepartmentUseCase.lookup(missingDepartmentId))
                .satisfies(result -> {
                    assertThat(result.exists()).isFalse();
                    assertThat(result.active()).isFalse();
                    assertThat(result.departmentId()).isEqualTo(missingDepartmentId);
                });

        var reconstitutedStaff = staffStore.findById(staff.getStaffId()).orElseThrow();
        reconstitutedStaff.deactivate();
        staffStore.save(reconstitutedStaff);
        assertThat(lookupStaffIdentityUseCase.lookup(staff.getStaffId()).active()).isFalse();

        var reconstitutedDepartment = departmentStore.findById(department.getDepartmentId())
                .orElseThrow();
        reconstitutedDepartment.deactivate(false);
        departmentStore.save(reconstitutedDepartment);
        assertThat(lookupDepartmentUseCase.lookup(department.getDepartmentId()).active())
                .isFalse();

        UUID patientId = UUID.randomUUID();
        var patientAccount = createAccountUseCase.execute(
                "patient." + UUID.randomUUID().toString().substring(0, 12),
                "Patient123!",
                null,
                patientId,
                Role.PATIENT);
        var verified = verifyCredentialsUseCase.execute(
                patientAccount.getUsername(), "Patient123!");
        assertThat(verified.accountId()).isEqualTo(patientAccount.getAccountId());
        assertThat(verified.role()).isEqualTo(Role.PATIENT);
        assertThat(verified.patientId()).isEqualTo(patientId);
        assertThat(verified.staffId()).isNull();
    }
}
