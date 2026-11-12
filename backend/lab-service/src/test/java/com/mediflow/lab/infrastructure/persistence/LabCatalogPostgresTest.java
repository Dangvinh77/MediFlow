package com.mediflow.lab.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mediflow.lab.domain.model.LabServiceCatalogItem;
import com.mediflow.lab.domain.model.LabValueType;
import com.mediflow.lab.infrastructure.persistence.adapter.JdbcLabCatalogAdapter;

@Testcontainers(disabledWithoutDocker = true)
class LabCatalogPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    JdbcTemplate jdbc;
    JdbcLabCatalogAdapter adapter;

    @BeforeEach void migrate() {
        DriverManagerDataSource ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        jdbc = new JdbcTemplate(ds);
        adapter = new JdbcLabCatalogAdapter(jdbc);
    }

    @Test void demoCatalogueIsServedByGroupWithUnreviewedRanges() {
        assertThat(adapter.activeServices(null)).extracting(LabServiceCatalogItem::serviceCode)
                .containsExactlyInAnyOrder("CBC", "GLU", "HBSAG", "UA", "MICRO-STAIN");
        assertThat(adapter.activeServices("HEMATOLOGY")).extracting(LabServiceCatalogItem::serviceCode)
                .containsExactly("CBC");
        LabServiceCatalogItem cbc = adapter.service("CBC").orElseThrow();
        assertThat(cbc.priceCode()).isEqualTo("LAB-CBC");
        assertThat(cbc.panel()).isTrue();

        var template = adapter.activeTemplate("CBC").orElseThrow();
        assertThat(template.version()).isEqualTo(1);
        assertThat(template.fields()).extracting(f -> f.fieldCode()).containsExactly("WBC", "RBC", "HGB", "HCT", "PLT");
        var hgb = template.field("HGB").orElseThrow();
        assertThat(hgb.ranges()).hasSize(2).allSatisfy(range -> assertThat(range.reviewed()).isFalse());
        assertThat(adapter.activeTemplate("HBSAG").orElseThrow().field("HBSAG").orElseThrow().allowedOptions())
                .containsExactly("Âm tính", "Dương tính", "Chưa xác định");
        assertThat(adapter.activeTemplate("GLU").orElseThrow().field("GLU").orElseThrow().allowsOperator()).isTrue();
        assertThat(adapter.activeTemplate("UA").orElseThrow().field("PRO").orElseThrow().valueType())
                .isEqualTo(LabValueType.SEMI_QUANTITATIVE);
        assertThat(adapter.activeServices("UNKNOWN")).isEmpty();
    }

    @Test void templateVersionPinningKeepsTheVersionATestWasCreatedWith() {
        UUID testId = UUID.randomUUID();
        jdbc.update("INSERT INTO lab_test (test_id, record_id, patient_id, requesting_department_id, test_type, "
                + "requested_date) VALUES (?, ?, ?, ?, 'Công thức máu', ?)", testId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), LocalDate.parse("2026-11-11"));
        adapter.bind(testId, "CBC", "CBC", 1);

        jdbc.update("UPDATE lab_result_template SET active = false WHERE template_code = 'CBC' AND version = 1");
        jdbc.update("INSERT INTO lab_result_template (template_code, version, name, active) VALUES ('CBC', 2, 'CBC rút gọn', true)");
        jdbc.update("INSERT INTO lab_template_field (template_code, version, field_code, label, position, required, value_type) "
                + "VALUES ('CBC', 2, 'HGB', 'HGB', 1, true, 'QUANTITATIVE')");

        assertThat(adapter.activeTemplate("CBC").orElseThrow().version()).isEqualTo(2);
        var binding = adapter.binding(testId).orElseThrow();
        assertThat(binding.templateVersion()).isEqualTo(1);
        assertThat(adapter.template(binding.templateCode(), binding.templateVersion()).orElseThrow().fields()).hasSize(5);
    }

    @Test void onlyOneVersionOfATemplateIsActive() {
        jdbc.update("INSERT INTO lab_result_template (template_code, version, name, active) VALUES ('CBC', 2, 'x', false)");
        assertThatThrownBy(() -> jdbc.update("UPDATE lab_result_template SET active = true WHERE template_code = 'CBC' "
                + "AND version = 2")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void qualitativeFieldWithoutOptionsIsRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO lab_template_field (template_code, version, field_code, label, "
                + "position, required, value_type) VALUES ('HBSAG', 1, 'X', 'x', 2, false, 'QUALITATIVE')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
