package com.mediflow.inpatient.infrastructure.persistence.jpaEntity;

import com.mediflow.inpatient.domain.model.enums.TreatmentEntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dien_bien_dieu_tri")
public class TreatmentEntryJpaEntity {
    @Id @Column(name = "entry_id", nullable = false) public UUID maMucDienBien;
    @Column(name = "admission_id", nullable = false) public UUID maDotNoiTru;
    @Enumerated(EnumType.STRING) @Column(name = "entry_type", nullable = false, length = 32) public TreatmentEntryType loaiMuc;
    @Column(name = "content", nullable = false) public String noiDung;
    @Column(name = "authored_by", nullable = false) public UUID nguoiGhi;
    @Column(name = "recorded_at", nullable = false) public Instant thoiGianGhi;
    @Column(name = "correction_of_entry_id") public UUID maMucBiDinhChinh;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) public Instant taoLuc;
    public TreatmentEntryJpaEntity() { }
}
