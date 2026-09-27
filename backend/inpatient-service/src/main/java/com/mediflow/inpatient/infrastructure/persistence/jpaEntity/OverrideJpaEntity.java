package com.mediflow.inpatient.infrastructure.persistence.jpaEntity;

import com.mediflow.inpatient.domain.model.enums.OverrideType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "phe_duyet_ngoai_le_noi_tru")
public class OverrideJpaEntity {
    @Id @Column(name = "override_id", nullable = false) public UUID maPheDuyet;
    @Column(name = "admission_id", nullable = false) public UUID maDotNoiTru;
    @Enumerated(EnumType.STRING) @Column(name = "override_type", nullable = false, length = 24) public OverrideType loaiPheDuyet;
    @Column(name = "approved_by", nullable = false) public UUID nguoiDuyet;
    @Column(name = "approver_role", nullable = false, length = 32) public String vaiTroNguoiDuyet;
    @Column(name = "reason", nullable = false) public String lyDo;
    @Column(name = "approved_at", nullable = false) public Instant thoiGianDuyet;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) public Instant taoLuc;
    public OverrideJpaEntity() { }
}
