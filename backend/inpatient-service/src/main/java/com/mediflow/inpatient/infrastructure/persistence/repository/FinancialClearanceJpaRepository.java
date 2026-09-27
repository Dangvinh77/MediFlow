package com.mediflow.inpatient.infrastructure.persistence.repository;

import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.FinancialClearanceJpaEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinancialClearanceJpaRepository extends JpaRepository<FinancialClearanceJpaEntity, UUID> {
    @Query("select clearance.hetHanLuc from FinancialClearanceJpaEntity clearance "
            + "where clearance.maXacNhan = :clearanceId")
    Optional<Instant> findExpiryById(@Param("clearanceId") UUID clearanceId);
}
