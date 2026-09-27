package com.mediflow.inpatient.infrastructure.persistence.jpaEntity;

import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lich_su_trang_thai_noi_tru")
public class AdmissionStatusHistoryJpaEntity {
    @Id @Column(name = "history_id", nullable = false) public UUID maLichSu;
    @Column(name = "admission_id", nullable = false) public UUID maDotNoiTru;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 32) public AdmissionStatus trangThaiTruoc;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 32) public AdmissionStatus trangThaiSau;
    @Column(name = "actor_id") public UUID nguoiThucHien;
    @Column(name = "reason") public String lyDo;
    @Column(name = "correlation_id", nullable = false, length = 100) public String maTuongQuan;
    @Column(name = "changed_at", nullable = false) public Instant thoiGianThayDoi;
    public AdmissionStatusHistoryJpaEntity() { }
}
