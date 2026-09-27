package com.mediflow.inpatient.infrastructure.persistence.jpaEntity;

import com.mediflow.inpatient.domain.model.enums.ClinicalOrderType;
import com.mediflow.inpatient.domain.model.enums.ExternalOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tham_chieu_y_lenh")
public class ClinicalOrderReferenceJpaEntity {
    @Id @Column(name = "order_ref_id", nullable = false) public UUID maThamChieu;
    @Column(name = "admission_id", nullable = false) public UUID maDotNoiTru;
    @Enumerated(EnumType.STRING) @Column(name = "order_type", nullable = false, length = 24) public ClinicalOrderType loaiYLenh;
    @Column(name = "external_order_id", nullable = false) public UUID maYLenhBenNgoai;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) public ExternalOrderStatus trangThai;
    @Column(name = "summary") public String tomTat;
    @Column(name = "event_version") public Integer phienBanSuKien;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) public Instant taoLuc;
    @Column(name = "updated_at", nullable = false, insertable = false) public Instant capNhatLuc;
    public ClinicalOrderReferenceJpaEntity() { }
}
