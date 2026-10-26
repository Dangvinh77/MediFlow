package com.mediflow.billing.infrastructure.persistence.adapter;
import com.mediflow.billing.application.dto.response.ScheduledPaymentQueueItem;
import com.mediflow.billing.application.port.out.ScheduledPaymentQueuePort;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ScheduledPaymentQueueAdapter implements ScheduledPaymentQueuePort {
    private final JdbcTemplate jdbc;
    public ScheduledPaymentQueueAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 3)
    public PageResult<ScheduledPaymentQueueItem> find(UUID patientId, PageQuery page) {
        String from = " FROM outpatient_registration_source s JOIN PAYMENT_REQUEST pr ON pr.payment_request_id=s.payment_request_id"
                + " WHERE s.registration_status='AWAITING_PAYMENT' AND s.source_type='EXAM'"
                + " AND pr.status IN ('PENDING','PARTIALLY_PAID') AND pr.expires_at > CURRENT_TIMESTAMP"
                + (patientId == null ? "" : " AND s.patient_id=?");
        var args = new ArrayList<Object>();
        if (patientId != null) args.add(patientId);
        Long count = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
        args.add(page.size()); args.add((long) page.page() * page.size());
        var rows = jdbc.query("SELECT s.registration_id,s.patient_id,s.department_id,pr.created_at,pr.expires_at" + from
                + " ORDER BY pr.created_at ASC,s.registration_id ASC LIMIT ? OFFSET ?", (rs, row) ->
                    new ScheduledPaymentQueueItem(rs.getObject("registration_id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getObject("department_id", UUID.class), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant()), args.toArray());
        return PageResult.of(rows, count == null ? 0 : count, page.page(), page.size());
    }
}
