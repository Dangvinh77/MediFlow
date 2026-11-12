package com.mediflow.lab.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.lab.domain.model.LabCriticalNotice;
import com.mediflow.lab.domain.model.LabFlag;
import com.mediflow.lab.infrastructure.persistence.adapter.JdbcLabCriticalNoticeAdapter;

@Testcontainers(disabledWithoutDocker = true)
class LabCriticalNoticePersistenceTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static final Instant AT = Instant.parse("2026-11-11T06:00:00Z");
    static JdbcTemplate jdbc;
    static JdbcLabCriticalNoticeAdapter adapter;

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        adapter = new JdbcLabCriticalNoticeAdapter(jdbc);
    }

    UUID test() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO lab_test(test_id, record_id, patient_id, requesting_department_id, test_type, requested_date,
                                     status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'Kali', DATE '2026-11-11', 'COMPLETED', now(), now())
                """, id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        return id;
    }

    @Test void oneNoticePerRevisionAndField() {
        UUID testId = test();
        UUID department = UUID.randomUUID();
        LabCriticalNotice first = LabCriticalNotice.raise(testId, 1, "K", "Kali", "7.2", "mmol/L",
                LabFlag.CRITICAL_HIGH, UUID.randomUUID(), department, AT);
        LabCriticalNotice replay = LabCriticalNotice.raise(testId, 1, "K", "Kali", "7.2", "mmol/L",
                LabFlag.CRITICAL_HIGH, UUID.randomUUID(), department, AT);
        assertThat(adapter.insertIfAbsent(first)).isTrue();
        assertThat(adapter.insertIfAbsent(replay)).isFalse();
        assertThat(adapter.find("OPEN", department)).extracting(LabCriticalNotice::getNoticeId)
                .containsExactly(first.getNoticeId());

        LabCriticalNotice stored = adapter.findForUpdate(first.getNoticeId()).orElseThrow();
        stored.acknowledge(UUID.randomUUID(), AT.plusSeconds(30));
        adapter.update(stored);
        assertThat(adapter.find("OPEN", department)).isEmpty();
        assertThat(adapter.find("ACKNOWLEDGED", null)).extracting(LabCriticalNotice::getNoticeId)
                .contains(first.getNoticeId());
    }
}
