package com.mediflow.patient.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PatientJpaRepository extends JpaRepository<PatientJpaEntity, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PatientJpaEntity p where p.patientId = :id")
    java.util.Optional<PatientJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByIdentityNumber(String identityNumber);

    boolean existsByIdentityNumberAndPatientIdNot(String identityNumber, UUID patientId);

    @Query("""
            select p from PatientJpaEntity p
            where p.archived = false and (:keyword = ''
               or lower(p.fullName) like lower(concat('%', :keyword, '%'))
               or p.identityNumber like concat(:keyword, '%'))
            """)
    Page<PatientJpaEntity> search(@Param("keyword") String keyword, Pageable pageable);
}
