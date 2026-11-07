package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.organization.application.port.out.RoomTypeRepository;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.RoomTypeDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JDBC adapter for the room type catalogue, bound to the Organization store. */
@Repository
public class JdbcRoomTypeRepository implements RoomTypeRepository {

    private static final String COLUMNS = "code, type_name, description, department_type, "
            + "single_per_department, system_defined, sort_order, created_at, updated_at";

    private static final RowMapper<RoomTypeDefinition> MAPPER = (rs, row) ->
            RoomTypeDefinition.reconstitute(
                    rs.getString("code"),
                    rs.getString("type_name"),
                    rs.getString("description"),
                    DepartmentType.valueOf(rs.getString("department_type")),
                    rs.getBoolean("single_per_department"),
                    rs.getBoolean("system_defined"),
                    rs.getInt("sort_order"),
                    instant(rs.getTimestamp("created_at")),
                    instant(rs.getTimestamp("updated_at")));

    private final JdbcTemplate jdbc;

    public JdbcRoomTypeRepository(@Qualifier("organizationDataSource") DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    @Override
    public List<RoomTypeDefinition> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM room_type_catalog", MAPPER);
    }

    @Override
    public Optional<RoomTypeDefinition> findByCode(String code) {
        return jdbc.query("SELECT " + COLUMNS + " FROM room_type_catalog WHERE code = ?", MAPPER, code)
                .stream().findFirst();
    }

    @Override
    public Optional<RoomTypeDefinition> lockByCode(String code) {
        return jdbc.query("SELECT " + COLUMNS + " FROM room_type_catalog WHERE code = ? FOR UPDATE",
                MAPPER, code).stream().findFirst();
    }

    @Override
    public boolean existsByName(String name, String excludedCode) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM room_type_catalog
                               WHERE lower(type_name) = lower(?)
                                 AND (CAST(? AS VARCHAR) IS NULL OR code <> CAST(? AS VARCHAR)))
                """, Boolean.class, name, excludedCode, excludedCode);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void insert(RoomTypeDefinition type) {
        jdbc.update("INSERT INTO room_type_catalog (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                type.getCode(), type.getName(), type.getDescription(), type.getDepartmentType().name(),
                type.isSinglePerDepartment(), type.isSystemDefined(), type.getSortOrder(),
                timestamp(type.getCreatedAt()), timestamp(type.getUpdatedAt()));
    }

    @Override
    public void update(RoomTypeDefinition type) {
        jdbc.update("UPDATE room_type_catalog SET type_name = ?, description = ?, updated_at = ? WHERE code = ?",
                type.getName(), type.getDescription(), timestamp(type.getUpdatedAt()), type.getCode());
    }

    @Override
    public void delete(String code) {
        jdbc.update("DELETE FROM room_type_catalog WHERE code = ?", code);
    }

    @Override
    public Map<String, Long> roomCounts() {
        Map<String, Long> counts = new HashMap<>();
        jdbc.query("SELECT room_type, count(*) AS rooms FROM room GROUP BY room_type",
                rs -> { counts.put(rs.getString("room_type"), rs.getLong("rooms")); });
        return counts;
    }

    @Override
    public int nextSortOrder() {
        Integer next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) + 10 FROM room_type_catalog", Integer.class);
        return next == null ? 10 : next;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
