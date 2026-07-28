package com.mediflow.patient.application.port.out;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.patient.domain.model.Patient;

import java.util.Optional;
import java.util.UUID;

/**
 * What this service needs from storage, expressed in domain terms.
 *
 * <p>Note what is absent: no {@code Pageable}, no {@code Page}, no JPA entity, no
 * {@code JpaRepository}. Those live in {@code infrastructure/persistence}, behind
 * {@code PatientPersistenceAdapter}. Swapping Postgres for anything else touches that adapter and
 * nothing above it.
 */
public interface PatientRepositoryPort {

    Patient save(Patient patient);

    Optional<Patient> findById(UUID maBenhNhan);

    /** @param keyword may be null — meaning "no filter", not "match empty string" */
    PageResult<Patient> search(String keyword, PageQuery pageQuery);

    boolean existsBySoCmnd(String soCmnd);

    void deleteById(UUID maBenhNhan);
}
