package com.mediflow.inpatient.infrastructure.persistence.jpaEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "xac_nhan_tai_chinh_noi_tru")
public class FinancialClearanceJpaEntity {
    @Id @Column(name = "clearance_id", nullable = false) public UUID maXacNhan;
    @Column(name = "event_id", nullable = false, unique = true) public UUID maSuKien;
    @Column(name = "invoice_id", nullable = false) public UUID maHoaDon;
    @Column(name = "account_id", nullable = false) public UUID maTaiKhoan;
    @Column(name = "admission_id", nullable = false) public UUID maDotNoiTru;
    @Column(name = "patient_id", nullable = false) public UUID maBenhNhan;
    @Column(name = "amount", nullable = false, precision = 19, scale = 2) public BigDecimal soTien;
    @Column(name = "currency", nullable = false, length = 3) public String tienTe;
    @Column(name = "payment_method", nullable = false, length = 32) public String phuongThucThanhToan;
    @Column(name = "expires_at") public Instant hetHanLuc;
    @Column(name = "emergency_override", nullable = false) public boolean capCuuNgoaiLe;
    @Column(name = "granted_at", nullable = false) public Instant thoiGianCap;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) public Instant taoLuc;
    public FinancialClearanceJpaEntity() { }
}
