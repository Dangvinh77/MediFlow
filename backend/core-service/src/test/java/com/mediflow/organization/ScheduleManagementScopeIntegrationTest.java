package com.mediflow.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.DepartmentRepository;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.infrastructure.persistence.entity.RoomEntity;
import com.mediflow.organization.infrastructure.persistence.repository.RoomJpaRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = com.mediflow.core.CoreServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ScheduleManagementScopeIntegrationTest {
    private static final String SECRET = "integration-scope-authority-secret-at-least-32-bytes";
    private static final AtomicLong IDENTITIES = new AtomicLong(220000000000L);

    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final PostgreSQLContainer<?> PATIENT_PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("mediflow.datasource.organization.url", PG::getJdbcUrl);
        registry.add("mediflow.datasource.organization.username", PG::getUsername);
        registry.add("mediflow.datasource.organization.password", PG::getPassword);
        registry.add("mediflow.datasource.patient.url", PATIENT_PG::getJdbcUrl);
        registry.add("mediflow.datasource.patient.username", PATIENT_PG::getUsername);
        registry.add("mediflow.datasource.patient.password", PATIENT_PG::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("mediflow.jwt.secret", () -> SECRET);
        registry.add("mediflow.organization.event-outbox.scheduling-enabled", () -> false);
        registry.add("eureka.client.enabled", () -> false);
    }

    @Autowired AccountRepository accounts;
    @Autowired DepartmentRepository departments;
    @Autowired StaffRepository staff;
    @Autowired RoomJpaRepository rooms;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;

    @Test
    void adminGrantIsDurableAndManagerAuthorityAndRoomDirectoryAreDepartmentScoped() throws Exception {
        Fixture fixture = fixture();
        Department managed = department("Target");
        Department outside = department("Outside");
        Staff doctor = createStaff("Doctor", managed, JobTitle.DOCTOR, "LIC-SCOPE");
        UUID consultationRoom = addRoom(managed, "Consult Room", "CONSULTATION", true);
        addRoom(managed, "Procedure Room", "PROCEDURE", true);
        addRoom(managed, "Closed Consult", "CONSULTATION", false);

        String path = "/api/v2/org/accounts/" + fixture.manager().getAccountId()
                + "/schedule-management-scope";
        ResponseEntity<String> unassigned = http.exchange(path, HttpMethod.GET,
                entity(null, fixture.admin(), Role.ADMIN, null, "trace-scope-unassigned"), String.class);
        assertThat(unassigned.getStatusCode().value()).isEqualTo(200);
        JsonNode emptyScope = mapper.readTree(unassigned.getBody()).get("data");
        assertThat(emptyScope.get("revision").asLong()).isZero();
        assertThat(emptyScope.get("departmentIds").isEmpty()).isTrue();
        assertThat(emptyScope.get("updatedAt").isNull()).isTrue();
        assertThat(emptyScope.get("updatedByAccountId").isNull()).isTrue();

        String key = "scope-grant-001";
        String correlation = "trace-scope-durable-001";
        String body = "{\"expectedRevision\":0,\"departmentIds\":[\""
                + managed.getDepartmentId() + "\"],\"reason\":\"Clinic schedule ownership\"}";
        ResponseEntity<String> grant = http.exchange(path, HttpMethod.PUT,
                entity(body, fixture.admin(), Role.ADMIN, key, correlation), String.class);
        assertThat(grant.getStatusCode().value()).as("grant response: %s", grant.getBody()).isEqualTo(200);
        JsonNode grantBody = mapper.readTree(grant.getBody());
        assertThat(grantBody.at("/data/revision").asLong()).isEqualTo(1);
        assertThat(grantBody.at("/data/departmentIds/0").asText()).isEqualTo(managed.getDepartmentId().toString());
        assertThat(grantBody.at("/data/updatedByAccountId").asText()).isEqualTo(fixture.admin().getAccountId().toString());
        assertThat(grant.getHeaders().getFirst("X-Correlation-Id")).isEqualTo(correlation);

        assertThat(jdbc.queryForObject("SELECT revision FROM schedule_management_scope WHERE account_id=?",
                Long.class, fixture.manager().getAccountId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT department_id FROM schedule_management_scope_department WHERE account_id=?",
                UUID.class, fixture.manager().getAccountId())).isEqualTo(managed.getDepartmentId());
        assertThat(jdbc.queryForObject("SELECT actor_account_id FROM schedule_management_scope_audit WHERE account_id=?",
                UUID.class, fixture.manager().getAccountId())).isEqualTo(fixture.admin().getAccountId());
        assertThat(jdbc.queryForObject("SELECT reason FROM schedule_management_scope_audit WHERE account_id=?",
                String.class, fixture.manager().getAccountId())).isEqualTo("Clinic schedule ownership");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_management_scope_command_receipt WHERE actor_account_id=?",
                Integer.class, fixture.admin().getAccountId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_event_outbox WHERE routing_key=? AND correlation_id=?",
                Integer.class, "organization.schedule-management-scope.changed", correlation)).isEqualTo(1);

        ResponseEntity<String> replay = http.exchange(path, HttpMethod.PUT,
                entity(body, fixture.admin(), Role.ADMIN, key, "trace-scope-replay"), String.class);
        assertThat(replay.getStatusCode().value()).isEqualTo(200);
        assertThat(replay.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        assertThat(mapper.readTree(replay.getBody()).get("data")).isEqualTo(grantBody.get("data"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_management_scope_audit WHERE account_id=?",
                Integer.class, fixture.manager().getAccountId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_event_outbox WHERE routing_key=? AND correlation_id=?",
                Integer.class, "organization.schedule-management-scope.changed", correlation)).isEqualTo(1);

        String changedReason = body.replace("Clinic schedule ownership", "Different decision");
        ResponseEntity<String> conflict = http.exchange(path, HttpMethod.PUT,
                entity(changedReason, fixture.admin(), Role.ADMIN, key, "trace-scope-conflict"), String.class);
        assertThat(conflict.getStatusCode().value()).isEqualTo(409);
        assertThat(mapper.readTree(conflict.getBody()).at("/error/code").asText())
                .isEqualTo("IDEMPOTENCY_CONFLICT");

        ResponseEntity<String> ownScope = http.exchange(path, HttpMethod.GET,
                entity(null, fixture.manager(), Role.MANAGER, null, "trace-scope-own"), String.class);
        assertThat(ownScope.getStatusCode().value()).isEqualTo(403);
        ResponseEntity<String> otherScope = http.exchange(
                "/api/v2/org/accounts/" + fixture.admin().getAccountId() + "/schedule-management-scope",
                HttpMethod.GET, entity(null, fixture.manager(), Role.MANAGER, null, "trace-scope-other"), String.class);
        assertThat(otherScope.getStatusCode().value()).isEqualTo(403);
        assertThat(mapper.readTree(otherScope.getBody()).at("/error/code").asText())
                .isEqualTo("FORBIDDEN");

        String serviceToken = token("service", "SYSTEM", "clinical-service");
        ResponseEntity<String> current = http.exchange("/api/v2/org/scheduling-authority?actorAccountId="
                        + fixture.manager().getAccountId() + "&departmentId=" + managed.getDepartmentId(),
                HttpMethod.GET, serviceEntity(serviceToken, "trace-authority-in-scope"), String.class);
        assertThat(current.getStatusCode().value()).isEqualTo(200);
        JsonNode authority = mapper.readTree(current.getBody()).get("data");
        assertThat(authority.get("currentRole").asText()).isEqualTo("MANAGER");
        assertThat(authority.get("staffActive").asBoolean()).isTrue();
        assertThat(authority.get("canManageSchedules").asBoolean()).isTrue();
        assertThat(authority.get("scopeRevision").asLong()).isEqualTo(1);

        ResponseEntity<String> outsideAuthority = http.exchange("/api/v2/org/scheduling-authority?actorAccountId="
                        + fixture.manager().getAccountId() + "&departmentId=" + outside.getDepartmentId(),
                HttpMethod.GET, serviceEntity(serviceToken, "trace-authority-outside"), String.class);
        assertThat(mapper.readTree(outsideAuthority.getBody()).at("/data/canManageSchedules").asBoolean()).isFalse();

        ResponseEntity<String> directory = http.exchange("/api/v2/org/consultation-rooms?departmentId="
                        + managed.getDepartmentId() + "&page=0&size=20", HttpMethod.GET,
                entity(null, fixture.manager(), Role.MANAGER, null, "trace-room-directory"), String.class);
        assertThat(directory.getStatusCode().value()).isEqualTo(200);
        JsonNode content = mapper.readTree(directory.getBody()).at("/data/content");
        assertThat(content.size()).isEqualTo(1);
        assertThat(content.get(0).get("roomId").asText()).isEqualTo(consultationRoom.toString());
        assertThat(content.get(0).size()).isEqualTo(4);

        ResponseEntity<String> outOfScopeDirectory = http.exchange("/api/v2/org/consultation-rooms?departmentId="
                        + outside.getDepartmentId(), HttpMethod.GET,
                entity(null, fixture.manager(), Role.MANAGER, null, "trace-room-outside"), String.class);
        assertThat(outOfScopeDirectory.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> eligibility = http.exchange("/api/v2/org/consultation-eligibility?doctorId="
                        + doctor.getStaffId() + "&departmentId=" + managed.getDepartmentId()
                        + "&roomId=" + consultationRoom, HttpMethod.GET,
                serviceEntity(serviceToken, "trace-consult-eligible"), String.class);
        assertThat(eligibility.getStatusCode().value()).isEqualTo(200);
        assertThat(mapper.readTree(eligibility.getBody()).at("/data/eligible").asBoolean()).isTrue();

        ResponseEntity<String> mismatched = http.exchange("/api/v2/org/consultation-eligibility?doctorId="
                        + doctor.getStaffId() + "&departmentId=" + outside.getDepartmentId()
                        + "&roomId=" + consultationRoom, HttpMethod.GET,
                serviceEntity(serviceToken, "trace-consult-ineligible"), String.class);
        assertThat(mapper.readTree(mismatched.getBody()).at("/data/eligible").asBoolean()).isFalse();
    }

    @Test
    void inactiveManagerCanBeRevokedButCannotReceiveNewDepartments() throws Exception {
        Fixture fixture = fixture();
        Department target = department("Revocation");
        fixture.manager().deactivate();
        accounts.save(fixture.manager());
        String path = "/api/v2/org/accounts/" + fixture.manager().getAccountId()
                + "/schedule-management-scope";

        ResponseEntity<String> grant = http.exchange(path, HttpMethod.PUT,
                entity("{\"expectedRevision\":0,\"departmentIds\":[\"" + target.getDepartmentId()
                                + "\"],\"reason\":\"Grant while inactive\"}",
                        fixture.admin(), Role.ADMIN, "inactive-grant", "trace-inactive-grant"), String.class);
        assertThat(grant.getStatusCode().value()).isEqualTo(422);
        assertThat(mapper.readTree(grant.getBody()).at("/error/code").asText()).isEqualTo("SCHEDULE_INELIGIBLE");

        ResponseEntity<String> revoke = http.exchange(path, HttpMethod.PUT,
                entity("{\"expectedRevision\":0,\"departmentIds\":[],\"reason\":\"Revoke inactive account\"}",
                        fixture.admin(), Role.ADMIN, "inactive-revoke", "trace-inactive-revoke"), String.class);
        assertThat(revoke.getStatusCode().value()).isEqualTo(200);
        assertThat(mapper.readTree(revoke.getBody()).at("/data/revision").asLong()).isEqualTo(1);
        assertThat(mapper.readTree(revoke.getBody()).at("/data/departmentIds").isEmpty()).isTrue();
    }

    @Test
    void currentDoctorAuthorityReflectsLiveAccountStaffAndDepartmentAndExplicitMissingFlags() throws Exception {
        Department firstDepartment = department("Current doctor first");
        Department secondDepartment = department("Current doctor second");
        Staff firstDoctor = createStaff("First doctor", firstDepartment, JobTitle.DOCTOR, "LIC-CURRENT-1");
        Staff secondDoctor = createStaff("Second doctor", secondDepartment, JobTitle.DOCTOR, "LIC-CURRENT-2");
        Account doctorAccount = accounts.save(Account.create(UUID.randomUUID(), username("current_doctor"),
                "test-hash", firstDoctor.getStaffId(), Role.DOCTOR));
        String serviceToken = token("service", "SYSTEM", "clinical-service");

        JsonNode active = currentDoctorAuthority(doctorAccount.getAccountId(), serviceToken,
                "trace-current-doctor-active");
        assertThat(active.get("accountExists").asBoolean()).isTrue();
        assertThat(active.get("accountActive").asBoolean()).isTrue();
        assertThat(active.get("currentRole").asText()).isEqualTo("DOCTOR");
        assertThat(active.get("staffId").asText()).isEqualTo(firstDoctor.getStaffId().toString());
        assertThat(active.get("staffActive").asBoolean()).isTrue();
        assertThat(active.get("departmentId").asText()).isEqualTo(firstDepartment.getDepartmentId().toString());
        assertThat(active.get("departmentActive").asBoolean()).isTrue();

        firstDoctor.deactivate();
        staff.save(firstDoctor);
        JsonNode inactiveStaff = currentDoctorAuthority(doctorAccount.getAccountId(), serviceToken,
                "trace-current-doctor-inactive-staff");
        assertThat(inactiveStaff.get("staffId").asText()).isEqualTo(firstDoctor.getStaffId().toString());
        assertThat(inactiveStaff.get("staffActive").asBoolean()).isFalse();
        assertThat(inactiveStaff.get("departmentId").asText()).isEqualTo(firstDepartment.getDepartmentId().toString());

        jdbc.update("UPDATE account SET staff_id=? WHERE account_id=?",
                secondDoctor.getStaffId(), doctorAccount.getAccountId());
        JsonNode changedStaff = currentDoctorAuthority(doctorAccount.getAccountId(), serviceToken,
                "trace-current-doctor-changed-staff");
        assertThat(changedStaff.get("staffId").asText()).isEqualTo(secondDoctor.getStaffId().toString());
        assertThat(changedStaff.get("staffActive").asBoolean()).isTrue();
        assertThat(changedStaff.get("departmentId").asText()).isEqualTo(secondDepartment.getDepartmentId().toString());

        doctorAccount.deactivate();
        accounts.save(doctorAccount);
        jdbc.update("UPDATE account SET staff_id=? WHERE account_id=?",
                secondDoctor.getStaffId(), doctorAccount.getAccountId());
        JsonNode inactiveAccount = currentDoctorAuthority(doctorAccount.getAccountId(), serviceToken,
                "trace-current-doctor-inactive-account");
        assertThat(inactiveAccount.get("accountActive").asBoolean()).isFalse();
        assertThat(inactiveAccount.get("staffId").asText()).isEqualTo(secondDoctor.getStaffId().toString());

        JsonNode missing = currentDoctorAuthority(UUID.randomUUID(), serviceToken,
                "trace-current-doctor-missing");
        assertThat(missing.get("accountExists").asBoolean()).isFalse();
        assertThat(missing.get("accountActive").asBoolean()).isFalse();
        assertThat(missing.get("currentRole").isNull()).isTrue();
        assertThat(missing.get("staffId").isNull()).isTrue();
        assertThat(missing.get("staffActive").asBoolean()).isFalse();
        assertThat(missing.get("departmentId").isNull()).isTrue();
        assertThat(missing.get("departmentActive").asBoolean()).isFalse();
    }

    @Test
    void auditFailureRollsBackScopeReceiptAndOutboxTogether() throws Exception {
        Fixture fixture = fixture();
        Department target = department("Atomicity");
        jdbc.execute("CREATE FUNCTION reject_scope_audit_test() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'injected audit failure'; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER reject_scope_audit_test BEFORE INSERT ON schedule_management_scope_audit FOR EACH ROW EXECUTE FUNCTION reject_scope_audit_test()");
        try {
            String path = "/api/v2/org/accounts/" + fixture.manager().getAccountId()
                    + "/schedule-management-scope";
            ResponseEntity<String> response = http.exchange(path, HttpMethod.PUT,
                    entity("{\"expectedRevision\":0,\"departmentIds\":[\"" + target.getDepartmentId()
                                    + "\"],\"reason\":\"Atomic audit decision\"}",
                            fixture.admin(), Role.ADMIN, "atomic-audit", "trace-atomic-audit"), String.class);
            assertThat(response.getStatusCode().value()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_management_scope WHERE account_id=?",
                    Integer.class, fixture.manager().getAccountId())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_management_scope_command_receipt WHERE idempotency_key=?",
                    Integer.class, "atomic-audit")).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_event_outbox WHERE routing_key=? AND correlation_id=?",
                    Integer.class, "organization.schedule-management-scope.changed", "trace-atomic-audit")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_scope_audit_test ON schedule_management_scope_audit");
            jdbc.execute("DROP FUNCTION reject_scope_audit_test()");
        }
    }

    private Fixture fixture() {
        Department home = department("Home");
        Staff managerStaff = createStaff("Manager", home, JobTitle.MANAGER, null);
        Staff adminStaff = createStaff("Admin", home, JobTitle.ADMINISTRATIVE, null);
        Account manager = accounts.save(Account.create(UUID.randomUUID(), username("manager"), "test-hash",
                managerStaff.getStaffId(), Role.MANAGER));
        Account admin = accounts.save(Account.create(UUID.randomUUID(), username("admin"), "test-hash",
                adminStaff.getStaffId(), Role.ADMIN));
        return new Fixture(manager, admin);
    }

    private Department department(String name) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return departments.save(Department.create(UUID.randomUUID(), name + " " + suffix,
                "S" + suffix, DepartmentType.CLINICAL, "Outpatient clinic"));
    }

    private Staff createStaff(String name, Department department, JobTitle jobTitle, String license) {
        return staff.save(Staff.create(name + " " + UUID.randomUUID().toString().substring(0, 8),
                department.getDepartmentId(), jobTitle, null, license, null, null,
                Long.toString(IDENTITIES.incrementAndGet())));
    }

    private UUID addRoom(Department department, String name, String type, boolean active) {
        UUID roomId = UUID.randomUUID();
        rooms.save(new RoomEntity(roomId, department.getDepartmentId(), name, type, active,
                Instant.now(), Instant.now()));
        return roomId;
    }

    private HttpEntity<String> entity(String body, Account actor, Role role,
            String idempotencyKey, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token("access", role.name(), actor.getAccountId().toString()));
        headers.set("X-Correlation-Id", correlationId);
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<String> serviceEntity(String serviceToken, String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceToken);
        headers.set("X-Correlation-Id", correlationId);
        return new HttpEntity<>(headers);
    }

    private JsonNode currentDoctorAuthority(UUID actorAccountId, String serviceToken, String correlationId)
            throws Exception {
        ResponseEntity<String> response = http.exchange("/api/v2/org/current-doctor-authority?actorAccountId="
                        + actorAccountId, HttpMethod.GET, serviceEntity(serviceToken, correlationId), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return mapper.readTree(response.getBody()).get("data");
    }

    private static String token(String type, String role, String subject) {
        Instant now = Instant.now();
        return Jwts.builder().subject(subject)
                .claim("type", type).claim("role", role)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(50)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static String username(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(Account manager, Account admin) {}
}
