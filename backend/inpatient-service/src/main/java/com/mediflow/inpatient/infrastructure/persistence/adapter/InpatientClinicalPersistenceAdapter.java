package com.mediflow.inpatient.infrastructure.persistence.adapter;

import com.mediflow.inpatient.application.port.out.ClinicalOrderReferenceRepositoryPort;
import com.mediflow.inpatient.application.port.out.TreatmentEntryRepositoryPort;
import com.mediflow.inpatient.domain.model.ClinicalOrderReference;
import com.mediflow.inpatient.domain.model.TreatmentEntry;
import com.mediflow.inpatient.domain.model.enums.ClinicalOrderType;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.ClinicalOrderReferenceJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.TreatmentEntryJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.mapper.InpatientPersistenceMapper;
import com.mediflow.inpatient.infrastructure.persistence.repository.ClinicalOrderReferenceJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.TreatmentEntryJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "mediflow.inpatient.persistence.enabled", havingValue = "true", matchIfMissing = true)
public class InpatientClinicalPersistenceAdapter implements TreatmentEntryRepositoryPort,
        ClinicalOrderReferenceRepositoryPort {
    private final TreatmentEntryJpaRepository treatments;
    private final ClinicalOrderReferenceJpaRepository references;
    private final InpatientPersistenceMapper mapper;

    public InpatientClinicalPersistenceAdapter(TreatmentEntryJpaRepository treatments,
                                               ClinicalOrderReferenceJpaRepository references,
                                               InpatientPersistenceMapper mapper) {
        this.treatments = treatments;
        this.references = references;
        this.mapper = mapper;
    }

    @Override
    public Optional<TreatmentEntry> findByAdmissionAndEntryId(UUID admissionId, UUID entryId) {
        return treatments.findByMaDotNoiTruAndMaMucDienBien(admissionId, entryId).map(mapper::toDomain);
    }

    @Override
    public TreatmentEntry save(TreatmentEntry entry) {
        TreatmentEntryJpaEntity row = treatments.findById(entry.entryId()).orElseGet(TreatmentEntryJpaEntity::new);
        return mapper.toDomain(treatments.save(mapper.copy(entry, row)));
    }

    @Override
    public Optional<ClinicalOrderReference> findByTypeAndExternalId(ClinicalOrderType type,
                                                                    UUID externalId) {
        return references.findByLoaiYLenhAndMaYLenhBenNgoai(type, externalId).map(mapper::toDomain);
    }

    @Override
    public List<ClinicalOrderReference> findByAdmissionId(UUID admissionId) {
        return references.findByMaDotNoiTruOrderByTaoLucAsc(admissionId).stream()
                .map(mapper::toDomain).toList();
    }

    @Override
    public ClinicalOrderReference save(ClinicalOrderReference reference) {
        ClinicalOrderReferenceJpaEntity row = references.findById(reference.referenceId())
                .orElseGet(ClinicalOrderReferenceJpaEntity::new);
        return mapper.toDomain(references.save(mapper.copy(reference, row)));
    }
}
