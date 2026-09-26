package com.mediflow.patient.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PatientJpaRepository extends JpaRepository<PatientJpaEntity, UUID> {

    @Query("""
            select p from PatientJpaEntity p
            where :keyword is null
               or lower(p.fullName) like lower(concat('%', :keyword, '%'))
               or p.identityNumber like concat(:keyword, '%')
            """)
    Page<PatientJpaEntity> search(@Param("keyword") String keyword, Pageable pageable);
}
