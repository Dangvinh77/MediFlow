package com.mediflow.notification.infrastructure.persistence.adapter;

import org.springframework.jdbc.core.JdbcTemplate;

import com.mediflow.notification.application.port.out.StaffCriticalNoticePort;
import com.mediflow.notification.domain.model.StaffCriticalNotice;

public class JdbcStaffCriticalNoticeAdapter implements StaffCriticalNoticePort {
    private final JdbcTemplate jdbc;

    public JdbcStaffCriticalNoticeAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean record(StaffCriticalNotice n) {
        return jdbc.update("""
                INSERT INTO staff_critical_notice(notice_id, source_event_id, lab_id, recipient_department_id, patient_id,
                                                  result_version, title, content)
                VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (notice_id) DO NOTHING
                """, n.noticeId(), n.sourceEventId(), n.labId(), n.recipientDepartmentId(), n.patientId(),
                n.resultVersion(), n.title(), n.content()) == 1;
    }
}
