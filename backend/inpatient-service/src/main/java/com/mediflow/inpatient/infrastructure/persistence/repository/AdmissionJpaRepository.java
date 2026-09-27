package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.AdmissionJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionJpaRepository extends JpaRepository<AdmissionJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AdmissionJpaEntity a where a.maDotNoiTru = :id")
    Optional<AdmissionJpaEntity> lockById(@Param("id") UUID id);

    Optional<AdmissionJpaEntity> findByMaYeuCauNoiTru(UUID requestId);

    @Query("select a from AdmissionJpaEntity a "
            + "where (:departmentId is null or a.maKhoa = :departmentId) "
            + "and (:patientId is null or a.maBenhNhan = :patientId) "
            + "and (:status is null or a.trangThai = :status) "
            + "and (:fromAt is null or a.thoiGianYeuCau >= :fromAt) "
            + "and (:toAt is null or a.thoiGianYeuCau < :toAt) "
            + "order by a.thoiGianYeuCau desc, a.maDotNoiTru")
    Page<AdmissionJpaEntity> search(@Param("departmentId") UUID departmentId,
                                    @Param("patientId") UUID patientId,
                                    @Param("status") com.mediflow.inpatient.domain.model.enums.AdmissionStatus status,
                                    @Param("fromAt") Instant fromAt,
                                    @Param("toAt") Instant toAt,
                                    Pageable pageable);
}
