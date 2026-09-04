package com.mediflow.pharmacy.infrastructure.persistence.repository;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PrescriptionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository cho aggregate đơn thuốc. */
public interface PrescriptionJpaRepository extends JpaRepository<PrescriptionJpaEntity, UUID> {

    /** @return các đơn thuộc một bệnh nhân */
    List<PrescriptionJpaEntity> findByPatientId(UUID patientId);

    /**
     * Khóa hàng prescription làm điểm tuần tự hóa cho mọi chuyển trạng thái của một đơn.
     *
     * @param id mã đơn thuốc
     * @return entity đã khóa hoặc rỗng
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PrescriptionJpaEntity p WHERE p.prescriptionId = :id")
    Optional<PrescriptionJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
