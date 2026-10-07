package com.mediflow.inpatient.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class InpatientSchemaIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inpatient_test")
            .withUsername("inpatient")
            .withPassword("inpatient");

    @Test
    void coreMigrationCreatesVietnameseTablesAndActiveAssignmentGuards() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        assertThat(jdbc.queryForObject("SELECT to_regclass('dot_noi_tru')", String.class))
                .isEqualTo("dot_noi_tru");
        assertThat(jdbc.queryForObject("SELECT to_regclass('phan_giuong')", String.class))
                .isEqualTo("phan_giuong");
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('tiep_nhan_su_kien_phau_thuat')", String.class))
                .isEqualTo("tiep_nhan_su_kien_phau_thuat");
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_phan_giuong_active_bed'",
                String.class)).containsIgnoringCase("UNIQUE").containsIgnoringCase("WHERE")
                .containsIgnoringCase("status").containsIgnoringCase("'ACTIVE'");
        assertThat(jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_phan_giuong_active_admission'",
                String.class)).containsIgnoringCase("UNIQUE").containsIgnoringCase("WHERE")
                .containsIgnoringCase("status").containsIgnoringCase("'ACTIVE'");
    }
}
