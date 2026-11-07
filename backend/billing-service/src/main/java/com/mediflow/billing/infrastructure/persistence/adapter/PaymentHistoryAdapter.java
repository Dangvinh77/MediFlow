package com.mediflow.billing.infrastructure.persistence.adapter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.billing.application.dto.response.PaymentHistoryItem;
import com.mediflow.billing.application.port.out.PaymentHistoryPort;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

@Repository
public class PaymentHistoryAdapter implements PaymentHistoryPort {
    private static final String FROM = """
             FROM PAYMENT_TRANSACTION t
             JOIN BILLING_ACCOUNT ba ON ba.account_id = t.account_id
             LEFT JOIN PAYMENT_REQUEST pr ON pr.payment_request_id = t.payment_request_id
             WHERE t.status = 'COMPLETED'
            """;

    private final JdbcTemplate jdbc;

    public PaymentHistoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true, timeout = 3)
    public PageResult<PaymentHistoryItem> findCompleted(UUID patientId, PageQuery page) {
        String where = FROM + (patientId == null ? "" : " AND ba.patient_id = ?");
        List<Object> args = new ArrayList<>();
        if (patientId != null) args.add(patientId);
        Long total = jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray());
        args.add(page.size());
        args.add((long) page.page() * page.size());
        var content = jdbc.query("""
                SELECT t.transaction_id, t.payment_request_id, ba.patient_id, ba.department_id, t.transaction_type,
                       pr.purpose, t.amount, t.currency, t.payment_method,
                       COALESCE(t.completed_at, t.created_at) AS moved_at
                """ + where + " ORDER BY moved_at DESC, t.transaction_id ASC LIMIT ? OFFSET ?",
                (rs, index) -> map(rs), args.toArray());
        return PageResult.of(content, total, page.page(), page.size());
    }

    private static PaymentHistoryItem map(ResultSet rs) throws SQLException {
        return new PaymentHistoryItem(rs.getObject("transaction_id", UUID.class),
                rs.getObject("payment_request_id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getObject("department_id", UUID.class), rs.getString("transaction_type"), rs.getString("purpose"),
                rs.getBigDecimal("amount"), rs.getString("currency").trim(), rs.getString("payment_method"),
                rs.getTimestamp("moved_at").toInstant());
    }
}
