package com.mediflow.patient.infrastructure.persistence;

import com.mediflow.patient.domain.model.Patient;
import org.springframework.stereotype.Component;

/**
 * Domain model ↔ JPA entity.
 *
 * <p>Hand-written rather than MapStruct: rebuilding the aggregate goes through
 * {@code Patient.khoiPhuc(...)} because the model has no public constructor and no setters.
 * MapStruct can be coerced into calling a factory, but the result is harder to read than these
 * twenty lines.
 */
@Component
public class PatientPersistenceMapper {

    public Patient toDomain(PatientJpaEntity e) {
        return Patient.khoiPhuc(
                e.getMaBenhNhan(), e.getHoTen(), e.getNgaySinh(), e.getGioiTinh(), e.getSoCmnd(),
                e.getDiaChi(), e.getSoDienThoai(), e.getEmail(), e.getBhytSo(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    /**
     * For a new patient {@code maBenhNhan} is null and Hibernate generates it. For an existing one
     * it is set, so this produces a merge rather than an insert. Timestamps are left to
     * {@code @CreationTimestamp}/{@code @UpdateTimestamp}.
     */
    public PatientJpaEntity toEntity(Patient p) {
        return PatientJpaEntity.builder()
                .maBenhNhan(p.getMaBenhNhan())
                .hoTen(p.getHoTen())
                .ngaySinh(p.getNgaySinh())
                .gioiTinh(p.getGioiTinh())
                .soCmnd(p.getSoCmnd())
                .diaChi(p.getDiaChi())
                .soDienThoai(p.getSoDienThoai())
                .email(p.getEmail())
                .bhytSo(p.getBhytSo())
                .build();
    }
}
