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
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.JobTitle;
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
    }
}
