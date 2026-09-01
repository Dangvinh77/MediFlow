package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.pharmacy.application.port.out.PrescriptionRepositoryPort;
import com.mediflow.pharmacy.domain.model.Prescription;
import com.mediflow.pharmacy.domain.model.PrescriptionLine;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PrescriptionJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.PrescriptionLineJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.PrescriptionJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link PrescriptionRepositoryPort} — đơn thuốc là một aggregate
 * (prescription + các dòng), nên save/read ở đây xử lý trọn cả cây. {@code application}
 * chỉ thấy port; mọi chuyện entity ↔ domain model đều nằm trong adapter này.
 */
@Component
@RequiredArgsConstructor
public class PrescriptionPersistenceAdapter implements PrescriptionRepositoryPort {

    private final PrescriptionJpaRepository jpaRepo;

    @Override
    public Prescription save(Prescription prescription) {
        PrescriptionJpaEntity entity = toEntity(prescription);
        PrescriptionJpaEntity saved = jpaRepo.save(entity); // cascade PERSIST/REMOVE theo @OneToMany
        return toDomain(saved);
    }

    @Override
    public Optional<Prescription> findById(UUID id) {
        return jpaRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<Prescription> findByPatient(UUID patientId) {
        return jpaRepo.findByPatientId(patientId).stream().map(this::toDomain).toList();
    }

    // ---- map entity ↔ domain (thủ công) ----

    private Prescription toDomain(PrescriptionJpaEntity e) {
        List<PrescriptionLine> lines = e.getLines().stream().map(this::toDomainLine).toList();
        return Prescription.restore(
                e.getPrescriptionId(), e.getRecordId(), e.getPatientId(), e.getDoctorId(),
                e.getDepartmentId(), e.getPrescribedDate(), e.getTotalAmount(), lines, e.getCreatedAt());
    }

    private PrescriptionLine toDomainLine(PrescriptionLineJpaEntity l) {
        return PrescriptionLine.restore(
                l.getLineId(), l.getDrugId(), l.getQuantity(), l.getUnitPrice(), l.getDosage(), l.getLineTotal());
    }

    private PrescriptionJpaEntity toEntity(Prescription p) {
        PrescriptionJpaEntity entity = PrescriptionJpaEntity.builder()
                .prescriptionId(p.getPrescriptionId())
                .recordId(p.getRecordId())
                .patientId(p.getPatientId())
                .doctorId(p.getDoctorId())
                .departmentId(p.getDepartmentId())
                .prescribedDate(p.getPrescribedDate())
                .totalAmount(p.getTotalAmount())
                .build();
        p.getLines().forEach(line -> entity.addLine(toEntityLine(line)));
        return entity;
    }

    private PrescriptionLineJpaEntity toEntityLine(PrescriptionLine l) {
        return PrescriptionLineJpaEntity.builder()
                .lineId(l.getLineId())
                .drugId(l.getDrugId())
                .quantity(l.getQuantity())
                .unitPrice(l.getUnitPrice())
                .dosage(l.getDosage())
                .lineTotal(l.getLineTotal())
                .build();
    }
}
