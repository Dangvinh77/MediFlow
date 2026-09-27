package com.mediflow.inpatient.infrastructure.persistence.adapter;

import com.mediflow.common.api.PageResult;
import com.mediflow.inpatient.application.dto.query.BedSearchQuery;
import com.mediflow.inpatient.application.port.out.BedAssignmentRepositoryPort;
import com.mediflow.inpatient.application.port.out.BedRepositoryPort;
import com.mediflow.inpatient.domain.model.Bed;
import com.mediflow.inpatient.domain.model.BedAssignment;
import com.mediflow.inpatient.domain.model.enums.BedAssignmentStatus;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.BedAssignmentJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.BedJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.mapper.InpatientPersistenceMapper;
import com.mediflow.inpatient.infrastructure.persistence.repository.BedAssignmentJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.BedJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "mediflow.inpatient.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class BedPersistenceAdapter implements BedRepositoryPort, BedAssignmentRepositoryPort {
    private final BedJpaRepository beds;
    private final BedAssignmentJpaRepository assignments;
    private final InpatientPersistenceMapper mapper;

    public BedPersistenceAdapter(BedJpaRepository beds, BedAssignmentJpaRepository assignments,
                                 InpatientPersistenceMapper mapper) {
        this.beds = beds;
        this.assignments = assignments;
        this.mapper = mapper;
    }

    @Override
    public Optional<Bed> findById(UUID bedId) {
        return beds.findById(bedId).map(mapper::toDomain);
    }

    @Override
    public Optional<Bed> findByIdForUpdate(UUID bedId) {
        return beds.lockById(bedId).map(mapper::toDomain);
    }

    @Override
    public List<Bed> findByIdsForUpdateInOrder(List<UUID> bedIds) {
        if (bedIds == null || bedIds.isEmpty()) {
            return List.of();
        }
        return beds.lockAllByIdsInOrder(bedIds.stream().distinct().sorted().toList())
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Bed save(Bed bed) {
        BedJpaEntity row = beds.findById(bed.bedId()).orElseGet(BedJpaEntity::new);
        return mapper.toDomain(beds.save(mapper.copy(bed, row)));
    }

    @Override
    public PageResult<Bed> search(BedSearchQuery query) {
        String wardCode = query.maKhu() == null || query.maKhu().isBlank() ? null : query.maKhu().trim();
        var page = beds.search(query.maKhoa(), wardCode, query.status(),
                PageRequest.of(query.phanTrang().page(), query.phanTrang().size()));
        return PageResult.of(page.getContent().stream().map(mapper::toDomain).toList(),
                page.getTotalElements(), page.getNumber(), page.getSize());
    }

    @Override
    public Optional<BedAssignment> findActiveByAdmissionId(UUID admissionId) {
        return assignments.findFirstByMaDotNoiTruAndTrangThai(admissionId, BedAssignmentStatus.ACTIVE)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<BedAssignment> findActiveByBedId(UUID bedId) {
        return assignments.findFirstByMaGiuongAndTrangThai(bedId, BedAssignmentStatus.ACTIVE)
                .map(mapper::toDomain);
    }

    @Override
    public BedAssignment save(BedAssignment assignment) {
        BedAssignmentJpaEntity row = assignments.findById(assignment.assignmentId())
                .orElseGet(BedAssignmentJpaEntity::new);
        return mapper.toDomain(assignments.save(mapper.copy(assignment, row)));
    }
}
