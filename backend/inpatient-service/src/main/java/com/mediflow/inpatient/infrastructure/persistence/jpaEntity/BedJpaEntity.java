package com.mediflow.inpatient.infrastructure.persistence.jpaEntity;

import com.mediflow.inpatient.domain.model.enums.BedStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "giuong_benh")
public class BedJpaEntity {
    @Id @Column(name = "bed_id", nullable = false) public UUID maGiuong;
    @Column(name = "department_id", nullable = false) public UUID maKhoa;
    @Column(name = "ward_code", nullable = false, length = 32) public String maKhu;
    @Column(name = "room_code", nullable = false, length = 32) public String maPhong;
    @Column(name = "bed_code", nullable = false, length = 32) public String maGiuongTrongPhong;
    @Column(name = "bed_type", nullable = false, length = 32) public String loaiGiuong;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 20) public BedStatus trangThai;
    @Column(name = "active", nullable = false) public boolean dangHoatDong;
    @Version @Column(name = "version", nullable = false) public long phienBan;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) public Instant taoLuc;
    @Column(name = "updated_at", nullable = false, insertable = false) public Instant capNhatLuc;
    public BedJpaEntity() { }
}
