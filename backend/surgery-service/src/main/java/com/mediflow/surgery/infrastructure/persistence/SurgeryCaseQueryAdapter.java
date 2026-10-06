package com.mediflow.surgery.infrastructure.persistence;

import com.mediflow.common.api.PageResult;
import com.mediflow.surgery.application.dto.response.SurgeryCaseBoardItem;
import com.mediflow.surgery.application.port.in.QuerySurgeryCasesUseCase;
import com.mediflow.surgery.application.port.out.SurgeryCaseQueryPort;
import com.mediflow.surgery.domain.model.SurgeryStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;

@Repository
@Profile("!test")
public class SurgeryCaseQueryAdapter implements SurgeryCaseQueryPort {
    private final NamedParameterJdbcTemplate jdbc;
    public SurgeryCaseQueryAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public PageResult<SurgeryCaseBoardItem> search(QuerySurgeryCasesUseCase.Filter filter) {
        var parameters = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        add(where, parameters, "c.department_id = :department", "department", filter.departmentId());
        add(where, parameters, "c.status = :status", "status", filter.status() == null ? null : filter.status().name());
        add(where, parameters, "c.requested_at >= :requestedFrom", "requestedFrom", timestamp(filter.requestedFrom()));
        add(where, parameters, "c.requested_at < :requestedUntil", "requestedUntil", timestamp(filter.requestedUntil()));
        // Planned-period filtering is half-open overlap, including drafts; never proof of reservation.
        add(where, parameters, "s.ends_at > :scheduledFrom", "scheduledFrom", timestamp(filter.scheduledFrom()));
        add(where, parameters, "s.starts_at < :scheduledUntil", "scheduledUntil", timestamp(filter.scheduledUntil()));
        String from = " FROM surgery_case c LEFT JOIN surgery_schedule s ON s.surgery_case_id = c.surgery_case_id";
        Long count = jdbc.queryForObject("SELECT count(*)" + from + where, parameters, Long.class);
        parameters.addValue("limit", filter.page().size());
        parameters.addValue("offset", (long) filter.page().page() * filter.page().size());
        var content = jdbc.query("""
                SELECT c.surgery_case_id, c.department_id, c.procedure_code, c.status,
                       c.revision, c.requested_at, s.schedule_id, s.starts_at, s.ends_at
                """ + from + where + " ORDER BY c.requested_at DESC, c.surgery_case_id ASC LIMIT :limit OFFSET :offset",
                parameters, (rs, row) -> new SurgeryCaseBoardItem(
                        rs.getObject("surgery_case_id", java.util.UUID.class),
                        rs.getObject("department_id", java.util.UUID.class), rs.getString("procedure_code"),
                        SurgeryStatus.valueOf(rs.getString("status")), rs.getLong("revision"),
                        rs.getTimestamp("requested_at").toInstant(), rs.getObject("schedule_id", java.util.UUID.class),
                        rs.getTimestamp("starts_at") == null ? null : rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at") == null ? null : rs.getTimestamp("ends_at").toInstant()));
        return PageResult.of(content, count == null ? 0 : count, filter.page().page(), filter.page().size());
    }

    private static void add(StringBuilder where, MapSqlParameterSource parameters,
            String clause, String name, Object value) {
        if (value != null) { where.append(" AND ").append(clause); parameters.addValue(name, value); }
    }
    private static Timestamp timestamp(java.time.Instant value) { return value == null ? null : Timestamp.from(value); }
}
