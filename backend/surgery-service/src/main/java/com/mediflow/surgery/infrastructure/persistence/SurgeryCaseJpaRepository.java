package com.mediflow.surgery.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SurgeryCaseJpaRepository extends JpaRepository<SurgeryCaseJpaEntity, UUID> {

    Optional<SurgeryCaseJpaEntity> findBySurgeryRequestId(UUID requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SurgeryCaseJpaEntity c where c.surgeryCaseId = :caseId")
    Optional<SurgeryCaseJpaEntity> lockById(@Param("caseId") UUID caseId);
}
