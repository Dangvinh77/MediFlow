package com.mediflow.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import javax.sql.DataSource;

import com.mediflow.organization.application.port.in.CreateDepartmentUseCase;
import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.in.CreateAccountUseCase;
import com.mediflow.organization.application.port.in.LookupDepartmentUseCase;
import com.mediflow.organization.application.port.in.LookupStaffIdentityUseCase;
import com.mediflow.organization.application.port.in.LookupRoomUseCase;
import com.mediflow.organization.application.port.in.LookupAccountIdentityUseCase;
import com.mediflow.organization.application.port.in.UpdateAccountStatusUseCase;
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
import com.mediflow.organization.web.handler.OrganizationGlobalExceptionHandler;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

/**
 * Verifies the real Flyway schema, JPA mappings, transaction boundary, and
 * RabbitMQ wiring together. Docker is required for this integration gate; a
 * missing Docker runtime must fail the build instead of silently skipping it.
 */
@SpringBootTest(classes = com.mediflow.core.CoreServiceApplication.class)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class OrganizationServiceIntegrationTest {
    private static final java.util.concurrent.atomic.AtomicLong IDENTITIES = new java.util.concurrent.atomic.AtomicLong(100000000000L);
    private static String nextIdentityNumber() { return Long.toString(IDENTITIES.incrementAndGet()); }


    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            "postgres:16-alpine");

    @Container
    static final PostgreSQLContainer<?> PATIENT_POSTGRES = new PostgreSQLContainer<>(
            "postgres:16-alpine");

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            "rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("mediflow.datasource.organization.url", POSTGRES::getJdbcUrl);
        registry.add("mediflow.datasource.organization.username", POSTGRES::getUsername);
        registry.add("mediflow.datasource.organization.password", POSTGRES::getPassword);
        registry.add("mediflow.datasource.patient.url", PATIENT_POSTGRES::getJdbcUrl);
        registry.add("mediflow.datasource.patient.username", PATIENT_POSTGRES::getUsername);
        registry.add("mediflow.datasource.patient.password", PATIENT_POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("eureka.client.enabled", () -> false);
        registry.add("mediflow.patient.outbox.scheduling-enabled", () -> false);
        registry.add("mediflow.organization.event-outbox.scheduling-enabled", () -> false);
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
    private LookupAccountIdentityUseCase lookupAccountIdentityUseCase;

    @Autowired
    private UpdateAccountStatusUseCase updateAccountStatusUseCase;

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
    @Qualifier("organizationDataSource")
    private DataSource organizationDataSource;

    @Autowired
    private com.mediflow.organization.infrastructure.messaging.OrganizationEventOutboxDispatcher organizationEventOutboxDispatcher;

    @Autowired
    private ConnectionFactory rabbitConnectionFactory;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrganizationGlobalExceptionHandler exceptionHandler;

    @Autowired
    private com.mediflow.organization.application.port.in.UpdateStaffUseCase updateStaff;
    @Autowired
    private com.mediflow.organization.application.port.in.UpdateDepartmentUseCase updateDepartment;
    @Autowired
    private com.mediflow.organization.application.port.in.ChangeStaffDepartmentUseCase transferStaff;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("organizationTransactionManager")
    private org.springframework.transaction.PlatformTransactionManager organizationTransactions;

    @Test
    void changingDepartmentHeadToNonDoctorCannotLeaveInvalidHeadReference() {
        var department = newDepartment();
        var doctor = newDoctor(department.getDepartmentId());
        changeHead(department, doctor.getStaffId());
        assertThrows(com.mediflow.organization.domain.exception.InvalidDepartmentHeadException.class,
                () -> changeToNurse(doctor.getStaffId()));
        assertThat(staffStore.findById(doctor.getStaffId()).orElseThrow().getJobTitle()).isEqualTo(JobTitle.DOCTOR);
        assertThat(departmentStore.findById(department.getDepartmentId()).orElseThrow().getDepartmentHeadId())
                .isEqualTo(doctor.getStaffId());
        changeHead(department, null);
        changeToNurse(doctor.getStaffId());
        assertThat(staffStore.findById(doctor.getStaffId()).orElseThrow().getJobTitle()).isEqualTo(JobTitle.NURSE);
        assertThat(departmentStore.findById(department.getDepartmentId()).orElseThrow().getDepartmentHeadId()).isNull();
    }

    @Test
    void unchangedHeadIdIsRevalidatedEvenForLegacyInvalidData() {
        var department = newDepartment();
        var doctor = newDoctor(department.getDepartmentId());
        changeHead(department, doctor.getStaffId());
        doctor.update("Former Doctor", JobTitle.NURSE, null, null, null, null);
        staffStore.save(doctor); // Deliberately reproduce a row left by the old implementation.
        assertThrows(com.mediflow.organization.domain.exception.InvalidDepartmentHeadException.class,
                () -> changeHead(department, doctor.getStaffId()));
        changeHead(department, null); // Explicit dismissal remains the repair path.
    }

    @Test
    void concurrentAppointmentAndTitleChangeSerializeInBothOrders() throws Exception {
        var department = newDepartment();
        var doctor = newDoctor(department.getDepartmentId());
        race(() -> changeHead(department, doctor.getStaffId()), () -> changeToNurse(doctor.getStaffId()),
                com.mediflow.organization.domain.exception.InvalidDepartmentHeadException.class);
        changeHead(department, null);
        race(() -> changeToNurse(doctor.getStaffId()), () -> changeHead(department, doctor.getStaffId()),
                com.mediflow.organization.domain.exception.InvalidDepartmentHeadException.class);
        assertThat(departmentStore.findById(department.getDepartmentId()).orElseThrow().getDepartmentHeadId()).isNull();
        assertThat(staffStore.findById(doctor.getStaffId()).orElseThrow().getJobTitle()).isEqualTo(JobTitle.NURSE);
    }

    @Test
    void departmentDeactivationAndStaffCreationSerializeInBothOrders() throws Exception {
        var inactiveFirst = newDepartment();
        race(() -> deactivate(inactiveFirst), () -> newDoctor(inactiveFirst.getDepartmentId()),
                com.mediflow.organization.domain.exception.DepartmentInactiveException.class);
        assertThat(departmentStore.findById(inactiveFirst.getDepartmentId()).orElseThrow().isActive()).isFalse();
        assertThat(staffStore.existsByDepartmentIdAndActiveTrue(inactiveFirst.getDepartmentId())).isFalse();

        var staffFirst = newDepartment();
        race(() -> newDoctor(staffFirst.getDepartmentId()), () -> deactivate(staffFirst),
                com.mediflow.organization.domain.exception.DepartmentHasActiveStaffException.class);
        assertThat(departmentStore.findById(staffFirst.getDepartmentId()).orElseThrow().isActive()).isTrue();
        assertThat(staffStore.existsByDepartmentIdAndActiveTrue(staffFirst.getDepartmentId())).isTrue();
    }

    @Test
    void departmentDeactivationAndIncomingTransferSerializeInBothOrders() throws Exception {
        var source = newDepartment();
        var doctor = newDoctor(source.getDepartmentId());
        var inactiveFirst = newDepartment();
        race(() -> deactivate(inactiveFirst),
                () -> transferStaff.execute(doctor.getStaffId(), inactiveFirst.getDepartmentId()),
                com.mediflow.organization.domain.exception.DepartmentInactiveException.class);
        assertThat(staffStore.existsByDepartmentIdAndActiveTrue(inactiveFirst.getDepartmentId())).isFalse();
        var transferFirst = newDepartment();
        race(() -> transferStaff.execute(doctor.getStaffId(), transferFirst.getDepartmentId()),
                () -> deactivate(transferFirst),
                com.mediflow.organization.domain.exception.DepartmentHasActiveStaffException.class);
        assertThat(departmentStore.findById(transferFirst.getDepartmentId()).orElseThrow().isActive()).isTrue();
        assertThat(staffStore.findById(doctor.getStaffId()).orElseThrow().getDepartmentId())
                .isEqualTo(transferFirst.getDepartmentId());
    }

    private com.mediflow.organization.domain.model.Department newDepartment() {
        return createDepartmentUseCase.execute("Audit Department", "A-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT),
                DepartmentType.CLINICAL, "Building A");
    }

    private com.mediflow.organization.domain.model.Staff newDoctor(UUID departmentId) {
        return createStaffUseCase.execute("Audit Doctor", departmentId, JobTitle.DOCTOR, "General",
                "LIC-" + UUID.randomUUID(), null, null, nextIdentityNumber());
    }

    private void changeHead(com.mediflow.organization.domain.model.Department department, UUID head) {
        updateDepartment.execute(department.getDepartmentId(), department.getDepartmentName(),
                department.getDepartmentType(), department.getLocation(), head, true);
    }

    private void deactivate(com.mediflow.organization.domain.model.Department department) {
        updateDepartment.execute(department.getDepartmentId(), department.getDepartmentName(),
                department.getDepartmentType(), department.getLocation(), null, false);
    }

    private void changeToNurse(UUID staffId) {
        updateStaff.execute(staffId, "Audit Nurse", JobTitle.NURSE, null, null, null, null);
    }

    /** Hold the winning use case before commit, then prove the competing use case waits and rechecks. */
    private void race(Runnable first, Runnable second, Class<? extends Throwable> rejection) throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var secondStarted = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var winner = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(
                    organizationTransactions).executeWithoutResult(status -> {
                        first.run();
                        ready.countDown();
                        try {
                            if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                                throw new IllegalStateException("Race release timed out");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                    }));
            try {
                assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var competitor = pool.submit(() -> { secondStarted.countDown(); second.run(); });
                assertThat(secondStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThrows(java.util.concurrent.TimeoutException.class,
                        () -> competitor.get(300, java.util.concurrent.TimeUnit.MILLISECONDS));
                release.countDown();
                winner.get(10, java.util.concurrent.TimeUnit.SECONDS);
                var failure = assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> competitor.get(10, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(failure.getCause()).isInstanceOf(rejection);
            } finally {
                release.countDown();
            }
        }
    }

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
                "integration.doctor@example.com", nextIdentityNumber());

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
    void createStaff_persistsDurableEventIntentInOrganizationStore() {
        var department = newDepartment();
        var staff = createStaffUseCase.execute(
                "Outbox Integration Staff",
                department.getDepartmentId(),
                JobTitle.NURSE,
                null,
                null,
                null,
                null, nextIdentityNumber());

        JdbcTemplate organizationJdbc = new JdbcTemplate(organizationDataSource);
        boolean eventTableExists = Boolean.TRUE.equals(organizationJdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_name = 'organization_event_outbox')
                """, Boolean.class));

        assertThat(eventTableExists).isTrue();
        String payload = organizationJdbc.queryForObject("""
                SELECT payload FROM organization_event_outbox
                WHERE routing_key='staff.created' AND payload LIKE ?
                """, String.class, "%" + staff.getStaffId() + "%");
        assertThat(payload).contains(staff.getStaffId().toString());
    }

    @Test
    void accountIdentityLookupReflectsCurrentRoleActiveStateAndStaffDepartment() {
        var department = newDepartment();
        var staff = newDoctor(department.getDepartmentId());
        var account = createAccountUseCase.execute(
                "identity." + UUID.randomUUID().toString().substring(0, 12),
                "StrongPass123!", staff.getStaffId(), null, Role.DOCTOR);

        var current = lookupAccountIdentityUseCase.lookup(account.getAccountId());
        assertThat(current.exists()).isTrue();
        assertThat(current.active()).isTrue();
        assertThat(current.accountId()).isEqualTo(account.getAccountId());
        assertThat(current.role()).isEqualTo("DOCTOR");
        assertThat(current.staffId()).isEqualTo(staff.getStaffId());
        assertThat(current.departmentId()).isEqualTo(department.getDepartmentId());
        assertThat(current.patientId()).isNull();

        updateAccountStatusUseCase.execute(account.getAccountId(), false);
        assertThat(lookupAccountIdentityUseCase.lookup(account.getAccountId()).active()).isFalse();
        assertThat(lookupAccountIdentityUseCase.lookup(UUID.randomUUID()).exists()).isFalse();
    }

    @Test
    void keyedStaffCreateReplaysSameReceiptAndRejectsChangedPayload() {
        var department = newDepartment();
        UUID actor = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        var first = createStaffUseCase.executeWithIdempotency(actor, key,
                "Keyed Staff", department.getDepartmentId(), JobTitle.NURSE,
                null, null, null, null, "012345678901");
        var replay = createStaffUseCase.executeWithIdempotency(actor, key,
                "Keyed Staff", department.getDepartmentId(), JobTitle.NURSE,
                null, null, null, null, "012345678901");

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.staff().getStaffId()).isEqualTo(first.staff().getStaffId());

        JdbcTemplate jdbc = new JdbcTemplate(organizationDataSource);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff WHERE staff_id=?",
                Integer.class, first.staff().getStaffId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM organization_event_outbox
                WHERE routing_key='staff.created' AND payload LIKE ?
                """, Integer.class, "%" + first.staff().getStaffId() + "%")).isEqualTo(1);
        assertThrows(com.mediflow.organization.domain.exception.StaffCreateIdempotencyConflictException.class,
                () -> createStaffUseCase.executeWithIdempotency(actor, key,
                        "Different Staff", department.getDepartmentId(), JobTitle.NURSE,
                        null, null, null, null, "012345678901"));
        assertThrows(com.mediflow.organization.domain.exception.StaffCreateIdempotencyConflictException.class,
                () -> createStaffUseCase.executeWithIdempotency(actor, key,
                        "Keyed Staff", department.getDepartmentId(), JobTitle.NURSE,
                        null, null, null, null, "012345678902"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff WHERE full_name IN (?,?)",
                Integer.class, "Keyed Staff", "Different Staff")).isEqualTo(1);
    }

    @Test
    void duplicateIdentityWithDifferentKeysRollsBackAndKeepsTheOriginalProfile() {
        var department = newDepartment();
        UUID actor = UUID.randomUUID();
        String identity = nextIdentityNumber();
        var first = createStaffUseCase.executeWithIdempotency(actor, "first-" + identity,
                "Original staff", department.getDepartmentId(), JobTitle.NURSE,
                null, null, null, null, identity).staff();
        String rejectedKey = "second-" + identity;
        var failure = assertThrows(DataIntegrityViolationException.class,
                () -> createStaffUseCase.executeWithIdempotency(actor, rejectedKey,
                        "Different name", department.getDepartmentId(), JobTitle.NURSE,
                        null, null, null, null, identity));
        var response = exceptionHandler.handleDataIntegrityViolation(failure);
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().error().code()).isEqualTo("STAFF_IDENTITY_DUPLICATE");
        JdbcTemplate jdbc = new JdbcTemplate(organizationDataSource);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff WHERE identity_number=?",
                Integer.class, identity)).isEqualTo(1);
        assertThat(staffStore.findById(first.getStaffId()).orElseThrow().getFullName()).isEqualTo("Original staff");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_creation_receipt WHERE actor_account_id=? AND idempotency_key=?",
                Integer.class, actor, rejectedKey)).isZero();
        changeToNurse(first.getStaffId());
        var target = newDepartment();
        transferStaff.execute(first.getStaffId(), target.getDepartmentId());
        assertThat(staffStore.findById(first.getStaffId()).orElseThrow().getIdentityNumber()).isEqualTo(identity);
    }

    @Test
    void concurrentCreateAcrossDepartmentsWithSameIdentityHasOneWinner() throws Exception {
        var departmentA = newDepartment();
        var departmentB = newDepartment();
        String identity = nextIdentityNumber();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> first = () -> {
                start.await();
                return createIdentityOrConflict(departmentA.getDepartmentId(), identity);
            };
            java.util.concurrent.Callable<Boolean> second = () -> {
                start.await();
                return createIdentityOrConflict(departmentB.getDepartmentId(), identity);
            };
            var a = executor.submit(first);
            var b = executor.submit(second);
            start.countDown();
            assertThat(java.util.List.of(a.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    b.get(15, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        JdbcTemplate jdbc = new JdbcTemplate(organizationDataSource);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff WHERE identity_number=?", Integer.class, identity)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM staff_creation_receipt r JOIN staff s ON s.staff_id=r.staff_id
                WHERE s.identity_number=?
                """, Integer.class, identity)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM organization_event_outbox o JOIN staff s
                ON o.payload LIKE '%' || s.staff_id::text || '%'
                WHERE o.routing_key='staff.created' AND s.identity_number=?
                """, Integer.class, identity)).isEqualTo(1);
    }

    private boolean createIdentityOrConflict(UUID departmentId, String identity) {
        try {
            createStaffUseCase.executeWithIdempotency(UUID.randomUUID(), UUID.randomUUID().toString(),
                    "Concurrent staff", departmentId, JobTitle.NURSE, null, null, null, null, identity);
            return true;
        } catch (DataIntegrityViolationException conflict) {
            assertThat(exceptionHandler.handleDataIntegrityViolation(conflict).getBody().error().code())
                    .isEqualTo("STAFF_IDENTITY_DUPLICATE");
            return false;
        }
    }

    @Test
    void identityMigrationPreservesExistingStaffWithoutFabricatingIdentity() {
        String schema = "staff_identity_upgrade";
        org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).locations("classpath:db/organization-migration").target("7").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(organizationDataSource);
        UUID department = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        jdbc.update("INSERT INTO " + schema + ".department(department_id,department_name,abbreviation,department_type) VALUES(?,?,?,?)",
                department, "Legacy department", "LEG", "CLINICAL");
        jdbc.update("INSERT INTO " + schema + ".staff(staff_id,full_name,department_id,job_title) VALUES(?,?,?,?)",
                staff, "Legacy staff", department, "NURSE");
        org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).locations("classpath:db/organization-migration").load().migrate();
        assertThat(jdbc.queryForObject("SELECT full_name FROM " + schema + ".staff WHERE staff_id=?", String.class, staff))
                .isEqualTo("Legacy staff");
        assertThat(jdbc.queryForObject("SELECT identity_number FROM " + schema + ".staff WHERE staff_id=?", String.class, staff)).isNull();
    }

    @Test
    void legacyStaffWithoutIdentityCanStillBeReadAndEdited() {
        var staff = newDoctor(newDepartment().getDepartmentId());
        JdbcTemplate jdbc = new JdbcTemplate(organizationDataSource);
        jdbc.update("UPDATE staff SET identity_number=NULL WHERE staff_id=?", staff.getStaffId());
        assertThat(staffStore.findById(staff.getStaffId()).orElseThrow().getIdentityNumber()).isNull();
        changeToNurse(staff.getStaffId());
        assertThat(staffStore.findById(staff.getStaffId()).orElseThrow().getIdentityNumber()).isNull();
    }

    @Test
    void concurrentAccountCreationReplaysOneDurableReceipt() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String username = "replay." + UUID.randomUUID();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<CreateAccountUseCase.CreationResult> create = () -> {
                start.await();
                return createAccountUseCase.executeWithIdempotency(actor, key, username,
                        "original-password", null, patient, Role.PATIENT);
            };
            var first = executor.submit(create);
            var second = executor.submit(create);
            start.countDown();
            var a = first.get(30, java.util.concurrent.TimeUnit.SECONDS);
            var b = second.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(a.account().getAccountId()).isEqualTo(b.account().getAccountId());
            assertThat(a.replayed()).isNotEqualTo(b.replayed());
            // A later call uses the committed DB receipt, not an in-memory result.
            var replay = createAccountUseCase.executeWithIdempotency(actor, key, username,
                    "original-password", null, patient, Role.PATIENT);
            assertThat(replay.replayed()).isTrue();
            assertThat(replay.account().getAccountId()).isEqualTo(a.account().getAccountId());
        }
        JdbcTemplate jdbc = new JdbcTemplate(organizationDataSource);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account WHERE username=?", Integer.class, username)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account_creation_receipt WHERE actor_account_id=? AND idempotency_key=?",
                Integer.class, actor, key)).isEqualTo(1);
        assertThrows(com.mediflow.common.exception.DuplicateResourceException.class,
                () -> createAccountUseCase.executeWithIdempotency(actor, key, username,
                        "changed-password", null, patient, Role.PATIENT));
        assertThrows(com.mediflow.common.exception.DuplicateResourceException.class,
                () -> createAccountUseCase.executeWithIdempotency(UUID.randomUUID(), key, username,
                        "original-password", null, patient, Role.PATIENT));
    }

    @Test
    void accountReceiptInsertFailureRollsBackTheAccount() {
        JdbcTemplate jdbc = new JdbcTemplate(organizationDataSource);
        String username = "rollback." + UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        jdbc.execute("""
                CREATE FUNCTION fail_account_receipt_insert() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'forced receipt failure'; END; $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER test_fail_account_receipt BEFORE INSERT ON account_creation_receipt
                FOR EACH ROW EXECUTE FUNCTION fail_account_receipt_insert()
                """);
        try {
            assertThrows(RuntimeException.class, () -> createAccountUseCase.executeWithIdempotency(actor, key,
                    username, "original-password", null, UUID.randomUUID(), Role.PATIENT));
        } finally {
            jdbc.execute("DROP TRIGGER test_fail_account_receipt ON account_creation_receipt");
            jdbc.execute("DROP FUNCTION fail_account_receipt_insert()");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account WHERE username=?", Integer.class, username)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM account_creation_receipt WHERE actor_account_id=? AND idempotency_key=?",
                Integer.class, actor, key)).isZero();
        var retry = createAccountUseCase.executeWithIdempotency(actor, key,
                username, "original-password", null, UUID.randomUUID(), Role.PATIENT);
        assertThat(retry.replayed()).isFalse();
    }

    @Test
    void outboxInsertFailureRollsBackNewStaffInOrganizationDatabase() {
        var department = newDepartment();
        String fullName = "Rollback Staff " + UUID.randomUUID();
        JdbcTemplate jdbc = new JdbcTemplate(organizationDataSource);
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_organization_event_outbox_insert()
                RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'forced outbox insert failure'; END; $$
                LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER test_fail_organization_event_outbox_insert
                BEFORE INSERT ON organization_event_outbox
                FOR EACH ROW EXECUTE FUNCTION fail_organization_event_outbox_insert()
                """);
        try {
            assertThrows(RuntimeException.class, () -> createStaffUseCase.execute(
                    fullName, department.getDepartmentId(), JobTitle.NURSE, null, null, null, null, nextIdentityNumber()));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS test_fail_organization_event_outbox_insert ON organization_event_outbox");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_organization_event_outbox_insert()");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff WHERE full_name=?",
                Integer.class, fullName)).isZero();
    }

    @Test
    void brokerRetryReusesTheOriginalOrganizationEventIdAndPayload() {
        JdbcTemplate jdbc = new JdbcTemplate(organizationDataSource);
        jdbc.update("DELETE FROM organization_event_outbox");
        var department = newDepartment();
        var staff = newDoctor(department.getDepartmentId());
        jdbc.update("DELETE FROM organization_event_outbox WHERE routing_key <> 'staff.created'");
        var original = jdbc.queryForMap("""
                SELECT event_id,payload,correlation_id,type_id FROM organization_event_outbox
                WHERE routing_key='staff.created' AND payload LIKE ?
                """, "%" + staff.getStaffId() + "%");
        UUID eventId = (UUID) original.get("event_id");
        String originalPayload = (String) original.get("payload");
        String queueName = "org-staff-retry-" + UUID.randomUUID();

        organizationEventOutboxDispatcher.dispatch();
        var afterUnroutable = jdbc.queryForMap(
                "SELECT status,attempts FROM organization_event_outbox WHERE event_id=?", eventId);
        assertThat(afterUnroutable.get("status")).isEqualTo("PENDING");
        assertThat((Integer) afterUnroutable.get("attempts")).isEqualTo(1);

        RabbitAdmin admin = new RabbitAdmin(rabbitConnectionFactory);
        Queue queue = new Queue(queueName, true, false, false);
        admin.declareQueue(queue);
        admin.declareBinding(BindingBuilder.bind(queue)
                .to(new TopicExchange(com.mediflow.organization.infrastructure.config.RabbitMqConfig.EVENT_EXCHANGE,
                        true, false))
                .with(com.mediflow.organization.application.event.StaffCreatedEvent.ROUTING_KEY));
        jdbc.update("UPDATE organization_event_outbox SET next_attempt_at=CURRENT_TIMESTAMP WHERE event_id=?", eventId);

        organizationEventOutboxDispatcher.dispatch();
        var message = rabbitTemplate.receive(queueName, 5000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(eventId.toString());
        assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo(original.get("correlation_id"));
        assertThat((String) message.getMessageProperties().getHeader("__TypeId__"))
                .isEqualTo(original.get("type_id"));
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(originalPayload);
        assertThat(jdbc.queryForObject("SELECT status FROM organization_event_outbox WHERE event_id=?",
                String.class, eventId)).isEqualTo("PUBLISHED");

        organizationEventOutboxDispatcher.dispatch();
        assertThat(rabbitTemplate.receive(queueName, 100)).isNull();
        admin.deleteQueue(queueName);
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
