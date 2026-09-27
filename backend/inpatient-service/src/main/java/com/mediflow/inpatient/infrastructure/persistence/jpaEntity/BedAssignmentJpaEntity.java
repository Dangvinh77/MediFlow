package com.mediflow.inpatient.infrastructure.persistence.jpaEntity;

import com.mediflow.inpatient.domain.model.enums.BedAssignmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "phan_giuong")
public class BedAssignmentJpaEntity {
    @Id @Column(name = "assignment_id", nullable = false) public UUID maPhanGiuong;
    @Column(name = "admission_id", nullable = false) public UUID maDotNoiTru;
    @Column(name = "bed_id", nullable = false) public UUID maGiuong;
    @Column(name = "assigned_by", nullable = false) public UUID nguoiPhanGiuong;
    @Column(name = "assigned_at", nullable = false) public Instant thoiGianPhanGiuong;
    @Column(name = "released_by") public UUID nguoiTraGiuong;
    @Column(name = "released_at") public Instant thoiGianTraGiuong;
    @Column(name = "release_reason") public String lyDoTraGiuong;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 16) public BedAssignmentStatus trangThai;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) public Instant taoLuc;
    public BedAssignmentJpaEntity() { }
}
