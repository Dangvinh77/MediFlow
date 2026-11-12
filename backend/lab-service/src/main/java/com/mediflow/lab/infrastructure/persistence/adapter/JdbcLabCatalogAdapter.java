package com.mediflow.lab.infrastructure.persistence.adapter;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.mediflow.lab.application.port.out.LabCatalogPort;
import com.mediflow.lab.domain.model.LabReferenceRange;
import com.mediflow.lab.domain.model.LabResultTemplate;
import com.mediflow.lab.domain.model.LabServiceCatalogItem;
import com.mediflow.lab.domain.model.LabTemplateField;
import com.mediflow.lab.domain.model.LabValueType;

@Component
public class JdbcLabCatalogAdapter implements LabCatalogPort {
    private static final String SERVICE_COLUMNS = "service_code, name, display_group, specimen_type, preparation, "
            + "turnaround_hours, price_code, template_code, panel, active";
    private final JdbcTemplate jdbc;

    public JdbcLabCatalogAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LabServiceCatalogItem> activeServices(String displayGroup) {
        if (displayGroup == null || displayGroup.isBlank()) {
            return jdbc.query("SELECT " + SERVICE_COLUMNS + " FROM lab_service_catalog WHERE active "
                    + "ORDER BY display_group, name, service_code", JdbcLabCatalogAdapter::service);
        }
        return jdbc.query("SELECT " + SERVICE_COLUMNS + " FROM lab_service_catalog WHERE active AND display_group = ? "
                + "ORDER BY name, service_code", JdbcLabCatalogAdapter::service, displayGroup);
    }

    @Override
    public Optional<LabServiceCatalogItem> service(String serviceCode) {
        return jdbc.query("SELECT " + SERVICE_COLUMNS + " FROM lab_service_catalog WHERE service_code = ?",
                JdbcLabCatalogAdapter::service, serviceCode).stream().findFirst();
    }

    @Override
    public Optional<LabResultTemplate> activeTemplate(String templateCode) {
        List<Integer> versions = jdbc.queryForList(
                "SELECT version FROM lab_result_template WHERE template_code = ? AND active", Integer.class, templateCode);
        return versions.isEmpty() ? Optional.empty() : template(templateCode, versions.get(0));
    }

    @Override
    public Optional<LabResultTemplate> template(String templateCode, int version) {
        List<String> names = jdbc.queryForList("SELECT name FROM lab_result_template WHERE template_code = ? AND version = ?",
                String.class, templateCode, version);
        if (names.isEmpty()) {
            return Optional.empty();
        }
        Map<String, List<LabReferenceRange>> ranges = new LinkedHashMap<>();
        jdbc.query("""
                SELECT field_code, sex, age_min_days, age_max_days, low, high, critical_low, critical_high, reviewed
                FROM lab_reference_range WHERE template_code = ? AND version = ? ORDER BY field_code, position
                """, rs -> {
            ranges.computeIfAbsent(rs.getString("field_code"), ignored -> new ArrayList<>()).add(new LabReferenceRange(
                    rs.getString("sex"), (Integer) rs.getObject("age_min_days"), (Integer) rs.getObject("age_max_days"),
                    rs.getBigDecimal("low"), rs.getBigDecimal("high"), rs.getBigDecimal("critical_low"),
                    rs.getBigDecimal("critical_high"), rs.getBoolean("reviewed")));
        }, templateCode, version);
        List<LabTemplateField> fields = jdbc.query("""
                SELECT field_code, label, position, required, value_type, unit, allowed_options, allows_operator
                FROM lab_template_field WHERE template_code = ? AND version = ? ORDER BY position, field_code
                """, (rs, n) -> new LabTemplateField(rs.getString("field_code"), rs.getString("label"),
                rs.getInt("position"), rs.getBoolean("required"), LabValueType.valueOf(rs.getString("value_type")),
                rs.getString("unit"), options(rs.getArray("allowed_options")), rs.getBoolean("allows_operator"),
                ranges.getOrDefault(rs.getString("field_code"), List.of())), templateCode, version);
        return Optional.of(new LabResultTemplate(templateCode, version, names.get(0), fields));
    }

    @Override
    public void bind(UUID testId, String serviceCode, String templateCode, int templateVersion) {
        jdbc.update("""
                INSERT INTO lab_test_template_binding (test_id, service_code, template_code, template_version)
                VALUES (?, ?, ?, ?)
                """, testId, serviceCode, templateCode, templateVersion);
    }

    @Override
    public Optional<TemplateBinding> binding(UUID testId) {
        return jdbc.query("""
                SELECT test_id, service_code, template_code, template_version FROM lab_test_template_binding
                WHERE test_id = ?
                """, (rs, n) -> new TemplateBinding(rs.getObject("test_id", UUID.class), rs.getString("service_code"),
                rs.getString("template_code"), rs.getInt("template_version")), testId).stream().findFirst();
    }

    private static LabServiceCatalogItem service(ResultSet rs, int n) throws SQLException {
        return new LabServiceCatalogItem(rs.getString("service_code"), rs.getString("name"),
                rs.getString("display_group"), rs.getString("specimen_type"), rs.getString("preparation"),
                (Integer) rs.getObject("turnaround_hours"), rs.getString("price_code"), rs.getString("template_code"),
                rs.getBoolean("panel"), rs.getBoolean("active"));
    }

    private static List<String> options(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}
