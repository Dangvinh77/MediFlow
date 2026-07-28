package com.mediflow.patient.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Spring Data repository. Deliberately package-private in intent: only
 * {@link PatientPersistenceAdapter} uses it, so Spring Data never leaks past this package.
 */
public interface PatientJpaRepository extends JpaRepository<PatientJpaEntity, UUID> {

    boolean existsBySoCmnd(String soCmnd);

    /**
     * A null keyword means "no filter". Name matches anywhere (people search by given name);
     * CMND matches by prefix, because a partial identity number is typed from the start.
     */
    @Query("""
            SELECT p FROM PatientJpaEntity p
            WHERE :keyword IS NULL
               OR LOWER(p.hoTen) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR p.soCmnd LIKE CONCAT(:keyword, '%')
            """)
    Page<PatientJpaEntity> search(@Param("keyword") String keyword, Pageable pageable);
}
