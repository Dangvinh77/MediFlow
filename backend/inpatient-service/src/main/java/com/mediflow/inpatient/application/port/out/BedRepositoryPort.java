package com.mediflow.inpatient.application.port.out;

import com.mediflow.common.api.PageResult;
import com.mediflow.inpatient.application.dto.query.BedSearchQuery;
import com.mediflow.inpatient.domain.model.Bed;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BedRepositoryPort {
    Optional<Bed> findById(UUID bedId);
    Optional<Bed> findByIdForUpdate(UUID bedId);
    List<Bed> findByIdsForUpdateInOrder(List<UUID> bedIds);
    Bed save(Bed bed);
    PageResult<Bed> search(BedSearchQuery query);
}
