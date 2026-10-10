package com.mediflow.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
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
import com.mediflow.organization.application.port.in.LookupRoomUseCase;
import com.mediflow.organization.application.port.in.VerifyCredentialsUseCase;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.mediflow.organization.infrastructure.persistence.repository.StaffJpaRepository;
import com.mediflow.organization.infrastructure.persistence.repository.RoomJpaRepository;
import com.mediflow.organization.infrastructure.persistence.repository.AccountJpaRepository;
import com.mediflow.organization.infrastructure.persistence.entity.RoomEntity;
import com.mediflow.organization.infrastructure.persistence.entity.AccountEntity;
import com.mediflow.organization.infrastructure.persistence.entity.DepartmentEntity;
import com.mediflow.organization.web.handler.GlobalExceptionHandler;
import java.time.Instant;

/**
 * Verifies the real Flyway schema, JPA mappings, transaction boundary, and
 * RabbitMQ wiring together. Docker is required for this integration gate; a
 * missing Docker runtime must fail the build instead of silently skipping it.
 */
@SpringBootTest
@Testcontainers
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
    private LookupRoomUseCase lookupRoomUseCase;

    @Autowired
    private StaffRepository staffStore;

    @Autowired
    private DepartmentRepository departmentStore;

    @Autowired
    private DepartmentJpaRepository departmentRepository;

    @Autowired
    private StaffJpaRepository staffRepository;

    @Autowired
    private RoomJpaRepository roomRepository;

    @Autowired
    private AccountJpaRepository accountRepository;

    @Autowired
    private GlobalExceptionHandler exceptionHandler;

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
        assertThat(staffLookup.eligibleTeamRoles()).containsExactly(
                "PRIMARY_SURGEON", "ASSISTANT_SURGEON", "ANESTHESIOLOGIST");

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

        UUID roomId = UUID.randomUUID();
        roomRepository.save(new RoomEntity(
                roomId,
                department.getDepartmentId(),
                "OR-INT-01",
                "OPERATING_ROOM",
                true,
                Instant.now(),
                Instant.now()));
        var roomLookup = lookupRoomUseCase.lookup(roomId);
        assertThat(roomLookup.exists()).isTrue();
        assertThat(roomLookup.active()).isTrue();
        assertThat(roomLookup.roomId()).isEqualTo(roomId);
        assertThat(roomLookup.departmentId()).isEqualTo(department.getDepartmentId());
        assertThat(roomLookup.roomType()).isEqualTo("OPERATING_ROOM");

        UUID missingRoomId = UUID.randomUUID();
        assertThat(lookupRoomUseCase.lookup(missingRoomId))
                .satisfies(result -> {
                    assertThat(result.exists()).isFalse();
                    assertThat(result.roomId()).isEqualTo(missingRoomId);
                });

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

    @Test
    void postgresUniqueConstraints_areMappedToStableApiErrors() {
        String abbreviation = "DUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        departmentRepository.saveAndFlush(new DepartmentEntity(
                UUID.randomUUID(), "Constraint Department A", abbreviation,
                DepartmentType.ADMINISTRATIVE, null, "Building C", true,
                Instant.now(), Instant.now()));

        DataIntegrityViolationException departmentViolation = assertThrows(
                DataIntegrityViolationException.class,
                () -> departmentRepository.saveAndFlush(new DepartmentEntity(
                        UUID.randomUUID(), "Constraint Department B", abbreviation,
                        DepartmentType.ADMINISTRATIVE, null, "Building D", true,
                        Instant.now(), Instant.now())));

        var departmentResponse = exceptionHandler.handleDataIntegrityViolation(departmentViolation);
        assertThat(departmentResponse.getStatusCode().value()).isEqualTo(409);
        assertThat(departmentResponse.getBody()).isNotNull();
        assertThat(departmentResponse.getBody().error().code())
                .isEqualTo("DEPARTMENT_ABBREVIATION_DUPLICATE");

        String username = "constraint-" + UUID.randomUUID().toString().substring(0, 8);
        accountRepository.saveAndFlush(new AccountEntity(
                UUID.randomUUID(), username, "bcrypt-hash", null, null, Role.ADMIN, true,
                null, Instant.now(), Instant.now()));

        DataIntegrityViolationException accountViolation = assertThrows(
                DataIntegrityViolationException.class,
                () -> accountRepository.saveAndFlush(new AccountEntity(
                        UUID.randomUUID(), username, "bcrypt-hash", null, null, Role.ADMIN, true,
                        null, Instant.now(), Instant.now())));

        var accountResponse = exceptionHandler.handleDataIntegrityViolation(accountViolation);
        assertThat(accountResponse.getStatusCode().value()).isEqualTo(409);
        assertThat(accountResponse.getBody()).isNotNull();
        assertThat(accountResponse.getBody().error().code())
                .isEqualTo("ACCOUNT_USERNAME_DUPLICATE");
    }
}
