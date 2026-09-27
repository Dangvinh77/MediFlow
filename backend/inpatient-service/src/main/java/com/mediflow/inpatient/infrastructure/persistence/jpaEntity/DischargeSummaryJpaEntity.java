package com.mediflow.inpatient.infrastructure.persistence.jpaEntity;

import com.mediflow.inpatient.domain.model.enums.DischargeOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tom_tat_ra_vien")
public class DischargeSummaryJpaEntity {
    @Id @Column(name = "summary_id", nullable = false) public UUID maTomTat;
    @Column(name = "admission_id", nullable = false, unique = true) public UUID maDotNoiTru;
    @Column(name = "diagnosis_summary", nullable = false) public String tomTatChanDoan;
    @Column(name = "treatment_summary", nullable = false) public String tomTatDieuTri;
    @Enumerated(EnumType.STRING) @Column(name = "outcome", nullable = false, length = 32) public DischargeOutcome ketQua;
    @Column(name = "follow_up_plan", nullable = false) public String keHoachTheoDoi;
    @Column(name = "approved_by", nullable = false) public UUID nguoiDuyet;
    @Column(name = "approved_at", nullable = false) public Instant thoiGianDuyet;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) public Instant taoLuc;
    public DischargeSummaryJpaEntity() { }
}
