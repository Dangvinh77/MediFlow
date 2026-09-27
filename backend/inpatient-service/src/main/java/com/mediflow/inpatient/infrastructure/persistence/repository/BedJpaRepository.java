package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.BedJpaEntity;
import com.mediflow.inpatient.domain.model.enums.BedStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BedJpaRepository extends JpaRepository<BedJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BedJpaEntity b where b.maGiuong = :id")
    java.util.Optional<BedJpaEntity> lockById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BedJpaEntity b where b.maGiuong in :ids order by b.maGiuong asc")
    List<BedJpaEntity> lockAllByIdsInOrder(@Param("ids") List<UUID> ids);

    @Query("select b from BedJpaEntity b "
            + "where (:departmentId is null or b.maKhoa = :departmentId) "
            + "and (:wardCode is null or b.maKhu = :wardCode) "
            + "and (:status is null or b.trangThai = :status) "
            + "order by b.maKhoa, b.maKhu, b.maPhong, b.maGiuongTrongPhong")
    Page<BedJpaEntity> search(@Param("departmentId") UUID departmentId,
                              @Param("wardCode") String wardCode,
                              @Param("status") BedStatus status,
                              Pageable pageable);
}
