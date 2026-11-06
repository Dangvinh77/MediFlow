package com.mediflow.organization;

import static org.assertj.core.api.Assertions.assertThat;

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

@SpringBootTest(classes = com.mediflow.core.CoreServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ScheduleManagementScopeIntegrationTest {
    private static final String SECRET = "integration-scope-authority-secret-at-least-32-bytes";
    private static final AtomicLong IDENTITIES = new AtomicLong(220000000000L);

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final PostgreSQLContainer<?> PATIENT_PG =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3.13-management-alpine");

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
    void currentDoctorAuthorityReflectsLiveAccountStaffAndDepartmentAndExplicitMissingFlags() throws Exception {
        Department firstDepartment = department("Current doctor first");
        Department secondDepartment = department("Current doctor second");
        Staff firstDoctor =
                createStaff("First doctor", firstDepartment, JobTitle.DOCTOR, "LIC-CURRENT-1");
        Staff secondDoctor =
                createStaff("Second doctor", secondDepartment, JobTitle.DOCTOR, "LIC-CURRENT-2");
        Account doctorAccount =
                accounts.save(
                        Account.create(
                                UUID.randomUUID(),
                                username("current_doctor"),
                                "test-hash",
                                firstDoctor.getStaffId(),
                                Role.DOCTOR));
        String serviceToken = token("service", "SYSTEM", "clinical-service");

        JsonNode active = currentDoctorAuthority(doctorAccount.getAccountId(), serviceToken,
                "trace-current-doctor-active");
        assertThat(active.get("accountExists").asBoolean()).isTrue();
        assertThat(active.get("accountActive").asBoolean()).isTrue();
        assertThat(active.get("currentRole").asText()).isEqualTo("DOCTOR");
        assertThat(active.get("staffId").asText()).isEqualTo(firstDoctor.getStaffId().toString());
        assertThat(active.get("staffActive").asBoolean()).isTrue();
        assertThat(active.get("departmentId").asText())
                .isEqualTo(firstDepartment.getDepartmentId().toString());
        assertThat(active.get("departmentActive").asBoolean()).isTrue();

        firstDoctor.deactivate();
        staff.save(firstDoctor);
        JsonNode inactiveStaff = currentDoctorAuthority(doctorAccount.getAccountId(), serviceToken,
                "trace-current-doctor-inactive-staff");
        assertThat(inactiveStaff.get("staffId").asText())
                .isEqualTo(firstDoctor.getStaffId().toString());
        assertThat(inactiveStaff.get("staffActive").asBoolean()).isFalse();
        assertThat(inactiveStaff.get("departmentId").asText())
                .isEqualTo(firstDepartment.getDepartmentId().toString());

        jdbc.update("UPDATE account SET staff_id=? WHERE account_id=?",
                secondDoctor.getStaffId(), doctorAccount.getAccountId());
        JsonNode changedStaff = currentDoctorAuthority(doctorAccount.getAccountId(), serviceToken,
                "trace-current-doctor-changed-staff");
        assertThat(changedStaff.get("staffId").asText())
                .isEqualTo(secondDoctor.getStaffId().toString());
        assertThat(changedStaff.get("staffActive").asBoolean()).isTrue();
        assertThat(changedStaff.get("departmentId").asText())
                .isEqualTo(secondDepartment.getDepartmentId().toString());

        doctorAccount.deactivate();
        accounts.save(doctorAccount);
        jdbc.update("UPDATE account SET staff_id=? WHERE account_id=?",
                secondDoctor.getStaffId(), doctorAccount.getAccountId());
        JsonNode inactiveAccount = currentDoctorAuthority(doctorAccount.getAccountId(), serviceToken,
                "trace-current-doctor-inactive-account");
        assertThat(inactiveAccount.get("accountActive").asBoolean()).isFalse();
        assertThat(inactiveAccount.get("staffId").asText())
                .isEqualTo(secondDoctor.getStaffId().toString());

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
    void appointedDoctorRetainsRoleAndLosesManagementImmediatelyOnRevocation() throws Exception {
        Fixture f = fixture();
        Department d = department("Head");
        Staff doctor = createStaff("Head Doctor", d, JobTitle.DOCTOR, "HEAD-LICENSE");
        Account account =
                accounts.save(
                        Account.create(
                                UUID.randomUUID(),
                                username("head"),
                                "hash",
                                doctor.getStaffId(),
                                Role.DOCTOR));
        String path = "/api/v2/org/departments/" + d.getDepartmentId() + "/head-appointments";
        String body =
                "{\"expectedRevision\":0,\"staffId\":\""
                        + doctor.getStaffId()
                        + "\",\"reason\":\"Appointment\"}";
        var granted =
                http.exchange(
                        path,
                        HttpMethod.PUT,
                        entity(body, f.admin(), Role.ADMIN, "head-grant", "head-trace"),
                        String.class);
        assertThat(granted.getStatusCode().value()).as(granted.getBody()).isEqualTo(200);
        assertThat(mapper.readTree(granted.getBody()).at("/data/revision").asLong()).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT department_head_id FROM department WHERE department_id=?",
                                UUID.class,
                                d.getDepartmentId()))
                .isEqualTo(doctor.getStaffId());
        var replay =
                http.exchange(
                        path,
                        HttpMethod.PUT,
                        entity(body, f.admin(), Role.ADMIN, "head-grant", "head-replay"),
                        String.class);
        assertThat(replay.getHeaders().getFirst("Idempotency-Replayed")).isEqualTo("true");
        assertThat(mapper.readTree(replay.getBody()).get("data"))
                .isEqualTo(mapper.readTree(granted.getBody()).get("data"));
        String authorityPath =
                "/api/v2/org/scheduling-authority?actorAccountId="
                        + account.getAccountId()
                        + "&departmentId="
                        + d.getDepartmentId();
        var auth =
                http.exchange(
                        authorityPath,
                        HttpMethod.GET,
                        serviceEntity(
                                token("service", "SYSTEM", "clinical-service"), "head-authority"),
                        String.class);
        assertThat(mapper.readTree(auth.getBody()).at("/data/currentRole").asText())
                .isEqualTo("DOCTOR");
        assertThat(mapper.readTree(auth.getBody()).at("/data/canManageSchedules").asBoolean())
                .isTrue();
        var caps =
                http.exchange(
                        "/api/v1/org/me/capabilities",
                        HttpMethod.GET,
                        entity(null, account, Role.DOCTOR, null, "caps"),
                        String.class);
        assertThat(caps.getStatusCode().value()).as(caps.getBody()).isEqualTo(200);
        assertThat(caps.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
        assertThat(mapper.readTree(caps.getBody()).at("/data/canPracticeAsDoctor").asBoolean())
                .isTrue();
        assertThat(mapper.readTree(caps.getBody()).at("/data/managementDepartments").size())
                .isEqualTo(1);
        var revoke =
                http.exchange(
                        path,
                        HttpMethod.PUT,
                        entity(
                                "{\"expectedRevision\":1,\"staffId\":null,\"reason\":\"End"
                                        + " appointment\"}",
                                f.admin(),
                                Role.ADMIN,
                                "head-revoke",
                                "head-revoke"),
                        String.class);
        assertThat(revoke.getStatusCode().value()).as(revoke.getBody()).isEqualTo(200);
        assertThat(mapper.readTree(revoke.getBody()).at("/data/history").size()).isEqualTo(1);
        auth =
                http.exchange(
                        authorityPath,
                        HttpMethod.GET,
                        serviceEntity(token("service", "SYSTEM", "clinical-service"), "head-after"),
                        String.class);
        assertThat(mapper.readTree(auth.getBody()).at("/data/canManageSchedules").asBoolean())
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT department_head_id FROM department WHERE department_id=?",
                                UUID.class,
                                d.getDepartmentId()))
                .isNull();
    }

    @Test
    void ordinaryDoctorAndLegacyProjectionDoNotGrantManagementAndCapabilityRolesStayHuman()
            throws Exception {
        Fixture f = fixture();
        Department d = department("Legacy projection");
        Staff doctor = createStaff("Ordinary doctor", d, JobTitle.DOCTOR, "ORDINARY");
        Account a =
                accounts.save(
                        Account.create(
                                UUID.randomUUID(),
                                username("ordinary"),
                                "hash",
                                doctor.getStaffId(),
                                Role.DOCTOR));
        jdbc.update(
                "UPDATE department SET department_head_id=? WHERE department_id=?",
                doctor.getStaffId(),
                d.getDepartmentId());
        var denied =
                http.exchange(
                        "/api/v2/org/consultation-rooms?departmentId=" + d.getDepartmentId(),
                        HttpMethod.GET,
                        entity(null, a, Role.DOCTOR, null, "ordinary-rooms"),
                        String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        var caps =
                http.exchange(
                        "/api/v1/org/me/capabilities",
                        HttpMethod.GET,
                        entity(null, a, Role.DOCTOR, null, "ordinary-caps"),
                        String.class);
        assertThat(caps.getStatusCode().value()).isEqualTo(200);
        assertThat(mapper.readTree(caps.getBody()).at("/data/managementDepartments").isEmpty())
                .isTrue();
        for (Role role : Role.values()) {
            jdbc.update(
                    "UPDATE account SET role=?,staff_id=?,patient_id=? WHERE account_id=?",
                    role.name(),
                    role == Role.PATIENT || role == Role.SYSTEM ? null : doctor.getStaffId(),
                    role == Role.PATIENT ? UUID.randomUUID() : null,
                    a.getAccountId());
            var response =
                    http.exchange(
                            "/api/v1/org/me/capabilities",
                            HttpMethod.GET,
                            role == Role.SYSTEM
                                    ? serviceEntity(
                                            token("service", "SYSTEM", "clinical-service"),
                                            "caps-role")
                                    : entity(null, a, role, null, "caps-role"),
                            String.class);
            assertThat(response.getStatusCode().value())
                    .as("Capabilities role %s", role)
                    .isEqualTo(role == Role.PATIENT || role == Role.SYSTEM ? 403 : 200);
        }
        jdbc.update(
                "UPDATE account SET role='DOCTOR',staff_id=?,patient_id=NULL WHERE account_id=?",
                doctor.getStaffId(),
                a.getAccountId());
        var forged =
                http.exchange(
                        "/api/v1/org/me/capabilities",
                        HttpMethod.GET,
                        entity(null, a, Role.ADMIN, null, "stale-caps-role"),
                        String.class);
        assertThat(forged.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void concurrentHeadReplacementHasOneWinnerAndStaleCommandPreservesHistory() throws Exception {
        Fixture f = fixture();
        Department d = department("Race");
        Staff one = createStaff("One", d, JobTitle.DOCTOR, "ONE");
        Staff two = createStaff("Two", d, JobTitle.DOCTOR, "TWO");
        accounts.save(
                Account.create(
                        UUID.randomUUID(), username("one"), "hash", one.getStaffId(), Role.DOCTOR));
        accounts.save(
                Account.create(
                        UUID.randomUUID(), username("two"), "hash", two.getStaffId(), Role.DOCTOR));
        String path = "/api/v2/org/departments/" + d.getDepartmentId() + "/head-appointments";
        var gate = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a =
                    executor.submit(
                            () -> {
                                gate.await();
                                return http.exchange(
                                        path,
                                        HttpMethod.PUT,
                                        entity(
                                                headBody(0, one.getStaffId()),
                                                f.admin(),
                                                Role.ADMIN,
                                                "race-one",
                                                "race-one"),
                                        String.class);
                            });
            var b =
                    executor.submit(
                            () -> {
                                gate.await();
                                return http.exchange(
                                        path,
                                        HttpMethod.PUT,
                                        entity(
                                                headBody(0, two.getStaffId()),
                                                f.admin(),
                                                Role.ADMIN,
                                                "race-two",
                                                "race-two"),
                                        String.class);
                            });
            gate.countDown();
            assertThat(
                            java.util.List.of(
                                    a.get().getStatusCode().value(),
                                    b.get().getStatusCode().value()))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM department_head_appointment WHERE"
                                            + " department_id=? AND ended_at IS NULL",
                                    Integer.class,
                                    d.getDepartmentId()))
                    .isEqualTo(1);
            var stale =
                    http.exchange(
                            path,
                            HttpMethod.PUT,
                            entity(
                                    headBody(0, one.getStaffId()),
                                    f.admin(),
                                    Role.ADMIN,
                                    "stale",
                                    "stale"),
                            String.class);
            assertThat(stale.getStatusCode().value()).isEqualTo(409);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM department_head_appointment WHERE"
                                            + " department_id=?",
                                    Integer.class,
                                    d.getDepartmentId()))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void appointmentRequiresSameClinicalDepartmentAndLiveDoctorIdentity() throws Exception {
        Fixture f = fixture();
        Department d = department("Eligibility");
        Department other = department("Other");
        Staff doctor = createStaff("Doctor", d, JobTitle.DOCTOR, "ELIG");
        Account a =
                accounts.save(
                        Account.create(
                                UUID.randomUUID(),
                                username("elig"),
                                "hash",
                                doctor.getStaffId(),
                                Role.DOCTOR));
        String path = "/api/v2/org/departments/" + d.getDepartmentId() + "/head-appointments";
        var denied =
                http.exchange(
                        path,
                        HttpMethod.PUT,
                        entity(headBody(0, doctor.getStaffId()), a, Role.DOCTOR, "self", "self"),
                        String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        var wrong =
                http.exchange(
                        "/api/v2/org/departments/" + other.getDepartmentId() + "/head-appointments",
                        HttpMethod.PUT,
                        entity(
                                headBody(0, doctor.getStaffId()),
                                f.admin(),
                                Role.ADMIN,
                                "wrong",
                                "wrong"),
                        String.class);
        assertThat(wrong.getStatusCode().value()).isEqualTo(422);
        var grant =
                http.exchange(
                        path,
                        HttpMethod.PUT,
                        entity(
                                headBody(0, doctor.getStaffId()),
                                f.admin(),
                                Role.ADMIN,
                                "live",
                                "live"),
                        String.class);
        assertThat(grant.getStatusCode().value()).isEqualTo(200);
        String ap =
                "/api/v2/org/scheduling-authority?actorAccountId="
                        + a.getAccountId()
                        + "&departmentId="
                        + d.getDepartmentId();
        for (String mutation :
                java.util.List.of(
                        "UPDATE staff SET status='INACTIVE' WHERE staff_id=?",
                        "UPDATE staff SET department_id='"
                                + other.getDepartmentId()
                                + "' WHERE staff_id=?")) {
            jdbc.update(mutation, doctor.getStaffId());
            var auth =
                    http.exchange(
                            ap,
                            HttpMethod.GET,
                            serviceEntity(
                                    token("service", "SYSTEM", "clinical-service"), "changed"),
                            String.class);
            assertThat(mapper.readTree(auth.getBody()).at("/data/canManageSchedules").asBoolean())
                    .isFalse();
            jdbc.update(
                    "UPDATE staff SET status='ACTIVE',department_id=? WHERE staff_id=?",
                    d.getDepartmentId(),
                    doctor.getStaffId());
        }
        jdbc.update("UPDATE account SET is_active=FALSE WHERE account_id=?", a.getAccountId());
        var auth =
                http.exchange(
                        ap,
                        HttpMethod.GET,
                        serviceEntity(token("service", "SYSTEM", "clinical-service"), "locked"),
                        String.class);
        assertThat(mapper.readTree(auth.getBody()).at("/data/canManageSchedules").asBoolean())
                .isFalse();
        jdbc.update(
                "UPDATE account SET is_active=TRUE,role='NURSE' WHERE account_id=?",
                a.getAccountId());
        auth =
                http.exchange(
                        ap,
                        HttpMethod.GET,
                        serviceEntity(
                                token("service", "SYSTEM", "clinical-service"), "changed-role"),
                        String.class);
        assertThat(mapper.readTree(auth.getBody()).at("/data/canManageSchedules").asBoolean())
                .isFalse();
        jdbc.update(
                "UPDATE account SET role='DOCTOR',staff_id=NULL WHERE account_id=?",
                a.getAccountId());
        auth =
                http.exchange(
                        ap,
                        HttpMethod.GET,
                        serviceEntity(
                                token("service", "SYSTEM", "clinical-service"),
                                "changed-association"),
                        String.class);
        assertThat(mapper.readTree(auth.getBody()).at("/data/canManageSchedules").asBoolean())
                .isFalse();
    }

    @Test
    void replacementPreservesAuditHistoryAndReceiptAndCannotGiveOneStaffTwoDepartments()
            throws Exception {
        Fixture f = fixture();
        Department d = department("Replacement");
        Department other = department("Second appointment");
        Staff one = createStaff("Previous head", d, JobTitle.DOCTOR, "REPLACE-1");
        Staff two = createStaff("Next head", d, JobTitle.DOCTOR, "REPLACE-2");
        accounts.save(
                Account.create(
                        UUID.randomUUID(),
                        username("previous"),
                        "hash",
                        one.getStaffId(),
                        Role.DOCTOR));
        accounts.save(
                Account.create(
                        UUID.randomUUID(),
                        username("next"),
                        "hash",
                        two.getStaffId(),
                        Role.DOCTOR));
        String path = "/api/v2/org/departments/" + d.getDepartmentId() + "/head-appointments";
        var initial =
                http.exchange(
                        path,
                        HttpMethod.GET,
                        entity(null, f.admin(), Role.ADMIN, null, "empty-head"),
                        String.class);
        assertThat(mapper.readTree(initial.getBody()).at("/data/revision").asLong()).isZero();
        assertThat(initial.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
        assertThat(mapper.readTree(initial.getBody()).at("/data/history").isEmpty()).isTrue();
        assertThat(
                        http.exchange(
                                        path,
                                        HttpMethod.PUT,
                                        entity(
                                                headBody(0, one.getStaffId()),
                                                f.admin(),
                                                Role.ADMIN,
                                                "replace-initial",
                                                "c".repeat(120)),
                                        String.class)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        var replaced =
                http.exchange(
                        path,
                        HttpMethod.PUT,
                        entity(
                                headBody(1, two.getStaffId()),
                                f.admin(),
                                Role.ADMIN,
                                "replace-next",
                                "replace-next"),
                        String.class);
        assertThat(replaced.getStatusCode().value()).as(replaced.getBody()).isEqualTo(200);
        JsonNode result = mapper.readTree(replaced.getBody()).get("data");
        assertThat(result.get("revision").asLong()).isEqualTo(2);
        assertThat(result.get("staffId").asText()).isEqualTo(two.getStaffId().toString());
        assertThat(result.get("history").size()).isEqualTo(2);
        assertThat(result.at("/history/0/endedAt").isNull()).isFalse();
        assertThat(result.at("/history/1/endedAt").isNull()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM department_head_audit WHERE department_id=?",
                                Integer.class,
                                d.getDepartmentId()))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM organization_event_outbox WHERE"
                                        + " correlation_id=?",
                                Integer.class,
                                "replace-next"))
                .isEqualTo(1);
        var collision =
                http.exchange(
                        path,
                        HttpMethod.PUT,
                        entity(
                                headBody(2, one.getStaffId()),
                                f.admin(),
                                Role.ADMIN,
                                "replace-next",
                                "replace-collision"),
                        String.class);
        assertThat(collision.getStatusCode().value()).isEqualTo(409);
        assertThat(mapper.readTree(collision.getBody()).at("/error/code").asText())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
        // Moving a staff member must deny authority but must not silently close their open term.
        jdbc.update(
                "UPDATE staff SET department_id=? WHERE staff_id=?",
                other.getDepartmentId(),
                two.getStaffId());
        var duplicate =
                http.exchange(
                        "/api/v2/org/departments/" + other.getDepartmentId() + "/head-appointments",
                        HttpMethod.PUT,
                        entity(
                                headBody(0, two.getStaffId()),
                                f.admin(),
                                Role.ADMIN,
                                "second-term",
                                "second-term"),
                        String.class);
        assertThat(duplicate.getStatusCode().value()).isEqualTo(422);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO"
                                            + " department_head_appointment(appointment_id,department_id,staff_id,account_id,started_at)"
                                            + " SELECT ?,?,staff_id,account_id,CURRENT_TIMESTAMP"
                                            + " FROM department_head_appointment WHERE staff_id=?"
                                            + " AND ended_at IS NULL",
                                        UUID.randomUUID(),
                                        other.getDepartmentId(),
                                        two.getStaffId()))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT position_type FROM department_head_appointment WHERE"
                                    + " staff_id=? AND ended_at IS NULL",
                                String.class,
                                two.getStaffId()))
                .isEqualTo("DEPARTMENT_HEAD");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE department_head_appointment SET"
                                            + " position_type='HEAD_NURSE' WHERE staff_id=?",
                                        two.getStaffId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM department_head_appointment WHERE staff_id=?"
                                        + " AND ended_at IS NULL",
                                Integer.class,
                                two.getStaffId()))
                .isEqualTo(1);
    }

    @Test
    void headAuditFailureRollsBackReplacementProjectionAndReceiptTogether() throws Exception {
        Fixture f = fixture();
        Department d = department("Head atomicity");
        Staff one = createStaff("Atomic original", d, JobTitle.DOCTOR, "ATOMIC-1");
        Staff two = createStaff("Atomic next", d, JobTitle.DOCTOR, "ATOMIC-2");
        accounts.save(
                Account.create(
                        UUID.randomUUID(),
                        username("atomic1"),
                        "hash",
                        one.getStaffId(),
                        Role.DOCTOR));
        accounts.save(
                Account.create(
                        UUID.randomUUID(),
                        username("atomic2"),
                        "hash",
                        two.getStaffId(),
                        Role.DOCTOR));
        String path = "/api/v2/org/departments/" + d.getDepartmentId() + "/head-appointments";
        assertThat(
                        http.exchange(
                                        path,
                                        HttpMethod.PUT,
                                        entity(
                                                headBody(0, one.getStaffId()),
                                                f.admin(),
                                                Role.ADMIN,
                                                "atomic-head-initial",
                                                "atomic-head-initial"),
                                        String.class)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        jdbc.execute(
                "CREATE FUNCTION reject_head_audit_test() RETURNS trigger AS $$ BEGIN RAISE"
                        + " EXCEPTION 'injected head audit failure'; END; $$ LANGUAGE plpgsql");
        jdbc.execute(
                "CREATE TRIGGER reject_head_audit_test BEFORE INSERT ON department_head_audit FOR"
                        + " EACH ROW EXECUTE FUNCTION reject_head_audit_test()");
        try {
            var response =
                    http.exchange(
                            path,
                            HttpMethod.PUT,
                            entity(
                                    headBody(1, two.getStaffId()),
                                    f.admin(),
                                    Role.ADMIN,
                                    "atomic-head-failure",
                                    "atomic-head-failure"),
                            String.class);
            assertThat(response.getStatusCode().is5xxServerError()).isTrue();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT department_head_id FROM department WHERE"
                                            + " department_id=?",
                                    UUID.class,
                                    d.getDepartmentId()))
                    .isEqualTo(one.getStaffId());
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT revision FROM department_head_state WHERE"
                                            + " department_id=?",
                                    Long.class,
                                    d.getDepartmentId()))
                    .isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM department_head_appointment WHERE"
                                            + " department_id=? AND ended_at IS NULL",
                                    Integer.class,
                                    d.getDepartmentId()))
                    .isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM department_head_appointment WHERE"
                                            + " department_id=?",
                                    Integer.class,
                                    d.getDepartmentId()))
                    .isEqualTo(1);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM department_head_receipt WHERE"
                                            + " idempotency_key=?",
                                    Integer.class,
                                    "atomic-head-failure"))
                    .isZero();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM organization_event_outbox WHERE"
                                            + " correlation_id=?",
                                    Integer.class,
                                    "atomic-head-failure"))
                    .isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_head_audit_test ON department_head_audit");
            jdbc.execute("DROP FUNCTION reject_head_audit_test()");
        }
    }

    @Test
    void headGrantRejectsInactiveNonclinicalAndNonDoctorTargetsWithoutCreatingRecords() {
        Fixture f = fixture();
        Department d = department("Invalid targets");
        Staff doctor = createStaff("Target", d, JobTitle.DOCTOR, "INVALID-TARGET");
        Account a =
                accounts.save(
                        Account.create(
                                UUID.randomUUID(),
                                username("target"),
                                "hash",
                                doctor.getStaffId(),
                                Role.DOCTOR));
        String path = "/api/v2/org/departments/" + d.getDepartmentId() + "/head-appointments";
        java.util.List<String> mutations =
                java.util.List.of(
                        "UPDATE department SET is_active=FALSE WHERE department_id='"
                                + d.getDepartmentId()
                                + "'",
                        "UPDATE department SET department_type='PARACLINICAL' WHERE department_id='"
                                + d.getDepartmentId()
                                + "'",
                        "UPDATE staff SET status='INACTIVE' WHERE staff_id='"
                                + doctor.getStaffId()
                                + "'",
                        "UPDATE staff SET job_title='NURSE' WHERE staff_id='"
                                + doctor.getStaffId()
                                + "'",
                        "UPDATE account SET is_active=FALSE WHERE account_id='"
                                + a.getAccountId()
                                + "'",
                        "UPDATE account SET role='NURSE' WHERE account_id='"
                                + a.getAccountId()
                                + "'",
                        "UPDATE account SET staff_id=NULL WHERE account_id='"
                                + a.getAccountId()
                                + "'");
        for (int i = 0; i < mutations.size(); i++) {
            jdbc.update(mutations.get(i));
            var response =
                    http.exchange(
                            path,
                            HttpMethod.PUT,
                            entity(
                                    headBody(0, doctor.getStaffId()),
                                    f.admin(),
                                    Role.ADMIN,
                                    "ineligible-" + i,
                                    "ineligible-" + i),
                            String.class);
            assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(422);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM department_head_appointment WHERE"
                                            + " department_id=?",
                                    Integer.class,
                                    d.getDepartmentId()))
                    .isZero();
            jdbc.update(
                    "UPDATE department SET is_active=TRUE,department_type='CLINICAL' WHERE"
                            + " department_id=?",
                    d.getDepartmentId());
            jdbc.update(
                    "UPDATE staff SET status='ACTIVE',job_title='DOCTOR' WHERE staff_id=?",
                    doctor.getStaffId());
            jdbc.update(
                    "UPDATE account SET is_active=TRUE,role='DOCTOR',staff_id=? WHERE account_id=?",
                    doctor.getStaffId(),
                    a.getAccountId());
        }
    }

    @Test
    void missingHeadTargetAndCommandHeadersAreBadRequestsAndDoNotRevoke() throws Exception {
        Fixture f = fixture();
        Department d = department("Required head target");
        Staff doctor = createStaff("Head target", d, JobTitle.DOCTOR, "REQUIRED-TARGET");
        accounts.save(
                Account.create(
                        UUID.randomUUID(),
                        username("required"),
                        "hash",
                        doctor.getStaffId(),
                        Role.DOCTOR));
        String path = "/api/v2/org/departments/" + d.getDepartmentId() + "/head-appointments";
        assertThat(
                        http.exchange(
                                        path,
                                        HttpMethod.PUT,
                                        entity(
                                                headBody(0, doctor.getStaffId()),
                                                f.admin(),
                                                Role.ADMIN,
                                                "required-initial",
                                                "required-initial"),
                                        String.class)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        var missingTarget =
                http.exchange(
                        path,
                        HttpMethod.PUT,
                        entity(
                                "{\"expectedRevision\":1,\"reason\":\"Missing target\"}",
                                f.admin(),
                                Role.ADMIN,
                                "missing-target",
                                "missing-target"),
                        String.class);
        assertThat(missingTarget.getStatusCode().value()).isEqualTo(400);
        var missingKey =
                http.exchange(
                        path,
                        HttpMethod.PUT,
                        entity(
                                headBody(1, doctor.getStaffId()),
                                f.admin(),
                                Role.ADMIN,
                                null,
                                "missing-key"),
                        String.class);
        assertThat(missingKey.getStatusCode().value()).isEqualTo(400);
        var missingCorrelation =
                entity(
                        headBody(1, doctor.getStaffId()),
                        f.admin(),
                        Role.ADMIN,
                        "missing-correlation",
                        "to-remove");
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(missingCorrelation.getHeaders());
        headers.remove("X-Correlation-Id");
        var response =
                http.exchange(
                        path,
                        HttpMethod.PUT,
                        new HttpEntity<>(missingCorrelation.getBody(), headers),
                        String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT revision FROM department_head_state WHERE department_id=?",
                                Long.class,
                                d.getDepartmentId()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT department_head_id FROM department WHERE department_id=?",
                                UUID.class,
                                d.getDepartmentId()))
                .isEqualTo(doctor.getStaffId());
    }

    private String headBody(long revision, UUID staffId) {
        return "{\"expectedRevision\":"
                + revision
                + ",\"staffId\":\""
                + staffId
                + "\",\"reason\":\"Admin decision\"}";
    }

    private Fixture fixture() {
        Department home = department("Home");
        Staff adminStaff = createStaff("Admin", home, JobTitle.ADMINISTRATIVE, null);
        Account admin =
                accounts.save(
                        Account.create(
                                UUID.randomUUID(),
                                username("admin"),
                                "test-hash",
                                adminStaff.getStaffId(),
                                Role.ADMIN));
        return new Fixture(admin);
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
        ResponseEntity<String> response =
                http.exchange(
                        "/api/v2/org/current-doctor-authority?actorAccountId=" + actorAccountId,
                        HttpMethod.GET,
                        serviceEntity(serviceToken, correlationId),
                        String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return mapper.readTree(response.getBody()).get("data");
    }

    private static String token(String type, String role, String subject) {
        Instant now = Instant.now();
        var builder = Jwts.builder().subject(subject)
                .claim("type", type).claim("role", role);
        if ("PATIENT".equals(role)) builder.claim("patientId", UUID.randomUUID().toString());
        return builder.issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(50)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static String username(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record Fixture(Account admin) {}
}
