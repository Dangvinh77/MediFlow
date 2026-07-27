package com.mediflow.patient.infrastructure.persistence;

import com.mediflow.patient.domain.model.GioiTinh;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Bảng BENH_NHAN. A dumb data holder — no business rules live here, they are all in
 * {@code domain.model.Patient}.
 *
 * <p>Every column name is written explicitly. The names are Vietnamese and irregular, so no naming
 * strategy can derive them (docs/ai/08-persistence-naming.md). No {@code @Data}: it would generate
 * {@code equals}/{@code hashCode} that break on Hibernate proxies.
 */
@Entity
@Table(name = "BENH_NHAN")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ma_benh_nhan", updatable = false, nullable = false)
    private UUID maBenhNhan;

    @Column(name = "ho_ten", length = 100, nullable = false)
    private String hoTen;

    @Column(name = "ngay_sinh", nullable = false)
    private LocalDate ngaySinh;

    @Enumerated(EnumType.STRING)
    @Column(name = "gioi_tinh", length = 1)
    private GioiTinh gioiTinh;

    @Column(name = "so_cmnd", length = 20, unique = true, nullable = false, updatable = false)
    private String soCmnd;

    @Column(name = "dia_chi", length = 255)
    private String diaChi;

    @Column(name = "so_dien_thoai", length = 15)
    private String soDienThoai;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "bhyt_so", length = 20)
    private String bhytSo;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
