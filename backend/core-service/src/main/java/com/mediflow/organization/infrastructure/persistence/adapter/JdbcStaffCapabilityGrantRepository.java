package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.organization.application.dto.response.StaffCapabilityGrantDTO;
import com.mediflow.organization.application.port.out.StaffCapabilityGrantRepository;
import com.mediflow.organization.domain.model.Role;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

@Repository
public class JdbcStaffCapabilityGrantRepository implements StaffCapabilityGrantRepository {
    private final JdbcTemplate jdbc;

    public JdbcStaffCapabilityGrantRepository(@Qualifier("organizationDataSource") DataSource source) {
        this.jdbc = new JdbcTemplate(source);
    }

    @Override
    public Optional<GrantTarget> lockTarget(UUID staffId) {
        var staff = jdbc.query("SELECT status='ACTIVE' FROM staff WHERE staff_id=? FOR UPDATE",
                (r, n) -> r.getBoolean(1), staffId);
        if (staff.isEmpty()) return Optional.empty();
        var accounts = jdbc.query(
                "SELECT role,is_active FROM account WHERE staff_id=? ORDER BY account_id FOR UPDATE",
                (r, n) -> new Object[] {r.getString(1), r.getBoolean(2)},
                staffId);
        if (accounts.size() != 1) return Optional.of(new GrantTarget(staffId, null, staff.getFirst(), false));
        var account = accounts.getFirst();
        return Optional.of(new GrantTarget(staffId, Role.valueOf((String) account[0]), staff.getFirst(), (boolean) account[1]));
    }

    @Override
    public boolean insertIfNoActive(StaffCapabilityGrantDTO grant) {
        return jdbc.update("""
                INSERT INTO staff_capability_grant
                    (grant_id,staff_id,capability,granted_by,grant_reason,granted_at)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT (staff_id,capability) WHERE revoked_at IS NULL DO NOTHING
                """, grant.grantId(), grant.staffId(), grant.capability(), grant.grantedBy(), grant.grantReason(),
                Timestamp.from(grant.grantedAt())) == 1;
    }

    @Override
    public Optional<StaffCapabilityGrantDTO> lockGrant(UUID grantId) {
        return jdbc.query("SELECT * FROM staff_capability_grant WHERE grant_id=? FOR UPDATE",
                (r, n) -> map(r), grantId).stream().findFirst();
    }

    @Override
    public void revoke(UUID grantId, UUID actorId, String reason, Instant at) {
        jdbc.update("UPDATE staff_capability_grant SET revoked_by=?,revocation_reason=?,revoked_at=? WHERE grant_id=?",
                actorId, reason, Timestamp.from(at), grantId);
    }

    @Override
    public List<StaffCapabilityGrantDTO> findByStaff(UUID staffId) {
        return jdbc.query("SELECT * FROM staff_capability_grant WHERE staff_id=? ORDER BY granted_at DESC,grant_id",
                (r, n) -> map(r), staffId);
    }

    @Override
    public LabReleaseData findLabRelease(UUID accountId) {
        return jdbc.query("""
                SELECT a.is_active,a.role,a.staff_id,COALESCE(s.status='ACTIVE',FALSE) AS staff_active,
                  EXISTS(SELECT 1 FROM staff_capability_grant g WHERE g.staff_id=a.staff_id
                         AND g.capability='LAB_RESULT_RELEASE' AND g.revoked_at IS NULL) AS granted
                FROM account a LEFT JOIN staff s ON s.staff_id=a.staff_id WHERE a.account_id=?
                """,
                (r, n) -> new LabReleaseData(true, r.getBoolean("is_active"), Role.valueOf(r.getString("role")),
                        r.getObject("staff_id", UUID.class), r.getBoolean("staff_active"), r.getBoolean("granted")),
                accountId).stream().findFirst().orElse(new LabReleaseData(false, false, null, null, false, false));
    }

    @Override
    public RecordReviewData findRecordReview(UUID accountId) {
        return jdbc.query("""
                SELECT a.is_active,a.role,a.staff_id,COALESCE(s.status='ACTIVE',FALSE) AS staff_active,
                  EXISTS(SELECT 1 FROM staff_capability_grant g WHERE g.staff_id=a.staff_id
                         AND g.capability='MEDICAL_RECORD_REVIEW' AND g.revoked_at IS NULL) AS granted
                FROM account a LEFT JOIN staff s ON s.staff_id=a.staff_id WHERE a.account_id=?
                """,
                (r, n) -> new RecordReviewData(true, r.getBoolean("is_active"), Role.valueOf(r.getString("role")),
                        r.getObject("staff_id", UUID.class), r.getBoolean("staff_active"), r.getBoolean("granted")),
                accountId).stream().findFirst().orElse(new RecordReviewData(false, false, null, null, false, false));
    }

    private static StaffCapabilityGrantDTO map(ResultSet r) throws SQLException {
        Timestamp revokedAt = r.getTimestamp("revoked_at");
        return new StaffCapabilityGrantDTO(r.getObject("grant_id", UUID.class), r.getObject("staff_id", UUID.class),
                r.getString("capability"), r.getObject("granted_by", UUID.class), r.getString("grant_reason"),
                r.getTimestamp("granted_at").toInstant(), r.getObject("revoked_by", UUID.class),
                r.getString("revocation_reason"), revokedAt == null ? null : revokedAt.toInstant());
    }
}
