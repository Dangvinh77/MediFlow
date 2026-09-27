package com.mediflow.inpatient.infrastructure.persistence.jpaEntity;

import com.mediflow.inpatient.domain.model.enums.SettlementOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quyet_toan_noi_tru")
public class SettlementSnapshotJpaEntity {
    @Id @Column(name = "settlement_id", nullable = false) public UUID maQuyetToan;
    @Column(name = "event_id", nullable = false, unique = true) public UUID maSuKien;
    @Column(name = "admission_id", nullable = false) public UUID maDotNoiTru;
    @Column(name = "account_id", nullable = false) public UUID maTaiKhoan;
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2) public BigDecimal tongTien;
    @Column(name = "insurance_amount", nullable = false, precision = 19, scale = 2) public BigDecimal baoHiemThanhToan;
    @Column(name = "patient_liability", nullable = false, precision = 19, scale = 2) public BigDecimal benhNhanPhaiTra;
    @Column(name = "completed_payments", nullable = false, precision = 19, scale = 2) public BigDecimal daThanhToan;
    @Column(name = "completed_refunds", nullable = false, precision = 19, scale = 2) public BigDecimal daHoanTien;
    @Column(name = "balance", nullable = false, precision = 19, scale = 2) public BigDecimal soDu;
    @Enumerated(EnumType.STRING) @Column(name = "outcome", nullable = false, length = 40) public SettlementOutcome ketQua;
    @Column(name = "completed_at", nullable = false) public Instant hoanTatLuc;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) public Instant taoLuc;
    public SettlementSnapshotJpaEntity() { }
}
