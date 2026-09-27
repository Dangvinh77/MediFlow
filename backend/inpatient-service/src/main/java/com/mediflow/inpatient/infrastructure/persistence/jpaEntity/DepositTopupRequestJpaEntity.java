package com.mediflow.inpatient.infrastructure.persistence.jpaEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "yeu_cau_bo_sung_tam_ung")
public class DepositTopupRequestJpaEntity {
    @Id @Column(name = "topup_request_id", nullable = false) public UUID maYeuCauBoSung;
    @Column(name = "event_id", nullable = false, unique = true) public UUID maSuKien;
    @Column(name = "admission_id", nullable = false) public UUID maDotNoiTru;
    @Column(name = "account_id", nullable = false) public UUID maTaiKhoan;
    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2) public BigDecimal soDuHienTai;
    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 2) public BigDecimal soTienYeuCau;
    @Column(name = "reason", nullable = false) public String lyDo;
    @Column(name = "requested_at", nullable = false) public Instant thoiGianYeuCau;
    public DepositTopupRequestJpaEntity() { }
}
