package com.mediflow.organization.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class StaffCapabilityGrantPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS staff_capability_grant");
        jdbc.execute("DROP TABLE IF EXISTS staff");
        jdbc.execute("CREATE TABLE staff (staff_id UUID PRIMARY KEY)");
        new ResourceDatabasePopulator(
                new ClassPathResource("db/organization-migration/V21__staff_capability_grant.sql"),
                new ClassPathResource("db/organization-migration/V22__medical_record_review_capability.sql"))
                .execute(dataSource);
    }

    @Test
    void checkConstraintAcceptsRecordReview() {
        UUID staffId = UUID.randomUUID();
        jdbc.update("INSERT INTO staff (staff_id) VALUES (?)", staffId);

        assertThatCode(() -> insertGrant(staffId, "MEDICAL_RECORD_REVIEW")).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertGrant(staffId, "PRESCRIBE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneActiveGrantPerStaffAndCapability() {
        UUID staffId = UUID.randomUUID();
        jdbc.update("INSERT INTO staff (staff_id) VALUES (?)", staffId);
        UUID reviewerGrant = insertGrant(staffId, "MEDICAL_RECORD_REVIEW");

        assertThatThrownBy(() -> insertGrant(staffId, "MEDICAL_RECORD_REVIEW"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertGrant(staffId, "LAB_RESULT_RELEASE")).doesNotThrowAnyException();

        jdbc.update("UPDATE staff_capability_grant SET revoked_by=?,revocation_reason=?,revoked_at=now() WHERE grant_id=?",
                UUID.randomUUID(), "Assignment ended", reviewerGrant);
        assertThatCode(() -> insertGrant(staffId, "MEDICAL_RECORD_REVIEW")).doesNotThrowAnyException();
    }

    @Test
    void findByStaffListsOnlyThatStaffNewestFirstWithRevocation() {
        UUID staffId = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        jdbc.update("INSERT INTO staff (staff_id) VALUES (?),(?)", staffId, other);
        UUID older = insertGrant(staffId, "LAB_RESULT_RELEASE");
        jdbc.update("UPDATE staff_capability_grant SET granted_at=now()-interval '1 day',revoked_by=?,"
                + "revocation_reason='Het phan cong',revoked_at=now() WHERE grant_id=?", UUID.randomUUID(), older);
        UUID newer = insertGrant(staffId, "LAB_RESULT_RELEASE");
        insertGrant(other, "LAB_RESULT_RELEASE");

        var listed = new JdbcStaffCapabilityGrantRepository(jdbc.getDataSource()).findByStaff(staffId);

        org.assertj.core.api.Assertions.assertThat(listed)
                .extracting(com.mediflow.organization.application.dto.response.StaffCapabilityGrantDTO::grantId)
                .containsExactly(newer, older);
        org.assertj.core.api.Assertions.assertThat(listed.get(1).revocationReason()).isEqualTo("Het phan cong");
        org.assertj.core.api.Assertions.assertThat(listed.get(0).revokedAt()).isNull();
    }

    private UUID insertGrant(UUID staffId, String capability) {
        UUID grantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO staff_capability_grant
                    (grant_id, staff_id, capability, granted_by, grant_reason, granted_at)
                VALUES (?, ?, ?, ?, ?, now())
                """, grantId, staffId, capability, UUID.randomUUID(), "test grant");
        return grantId;
    }
}
