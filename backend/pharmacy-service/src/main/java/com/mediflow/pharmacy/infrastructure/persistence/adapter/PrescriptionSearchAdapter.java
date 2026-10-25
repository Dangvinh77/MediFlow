package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.application.port.out.PrescriptionSearchPort;
import com.mediflow.pharmacy.domain.model.enums.PrescriptionStatus;
import java.util.HashMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PrescriptionSearchAdapter implements PrescriptionSearchPort {
    private final NamedParameterJdbcTemplate jdbc;
    public PrescriptionSearchAdapter(JdbcTemplate jdbc) { this.jdbc = new NamedParameterJdbcTemplate(jdbc); }

    @Override
    @Transactional(readOnly = true)
    public PageResult<UUID> search(String keyword, UUID patientId, PrescriptionStatus status, PageQuery page) {
        var args = new HashMap<String, Object>();
        var where = new StringBuilder(" WHERE 1=1");
        if (patientId != null) { where.append(" AND p.patient_id=:patient"); args.put("patient", patientId); }
        if (status != null) { where.append(" AND p.status=:status"); args.put("status", status.name()); }
        if (keyword != null && !keyword.isBlank()) {
            // Treat user wildcard characters literally; match persisted medication snapshots.
            String term = keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            where.append(" AND EXISTS (SELECT 1 FROM prescription_line l WHERE l.prescription_id=p.prescription_id AND l.drug_name_snapshot ILIKE :keyword)");
            args.put("keyword", "%" + term + "%");
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM prescription p" + where, args, Long.class);
        args.put("limit", page.size()); args.put("offset", (long) page.page() * page.size());
        var ids = jdbc.query("SELECT p.prescription_id FROM prescription p" + where
                + " ORDER BY p.created_at DESC, p.prescription_id DESC LIMIT :limit OFFSET :offset", args,
                (row, index) -> row.getObject("prescription_id", UUID.class));
        return PageResult.of(ids, total, page.page(), page.size());
    }
}
