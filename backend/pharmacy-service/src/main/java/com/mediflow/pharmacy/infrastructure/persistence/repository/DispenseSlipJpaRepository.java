package com.mediflow.pharmacy.infrastructure.persistence.repository;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.DispenseSlipJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository cho phiếu xuất thuốc. */
public interface DispenseSlipJpaRepository extends JpaRepository<DispenseSlipJpaEntity, UUID> {

    /** @return phiếu duy nhất của đơn nếu tồn tại */
    Optional<DispenseSlipJpaEntity> findByPrescriptionId(UUID prescriptionId);

    /**
     * Tìm phiếu theo đơn kèm khóa ghi để bảo vệ chuyển trạng thái terminal.
     *
     * @param prescriptionId mã đơn thuốc
     * @return phiếu đã khóa hoặc rỗng
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DispenseSlipJpaEntity d WHERE d.prescriptionId = :prescriptionId")
    Optional<DispenseSlipJpaEntity> findByPrescriptionIdForUpdate(
            @Param("prescriptionId") UUID prescriptionId);
}
