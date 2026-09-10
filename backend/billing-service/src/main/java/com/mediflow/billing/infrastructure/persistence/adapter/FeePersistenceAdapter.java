package com.mediflow.billing.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.billing.application.port.out.FeeRepositoryPort;
import com.mediflow.billing.domain.model.Fee;
import com.mediflow.billing.domain.model.FeeType;
import com.mediflow.billing.infrastructure.persistence.jpaEntity.FeeJpaEntity;
import com.mediflow.billing.infrastructure.persistence.repository.FeeJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link FeeRepositoryPort} — hiện thực ngoài {@code application}, đóng gói Spring Data
 * JPA. {@code application} chỉ thấy port (không biết JPA tồn tại); việc đổi entity ↔ domain model
 * nằm trọn ở đây, làm thủ công để quy tắc domain không lọt ra ngoài (giống pharmacy-service).
 */
@Component
@RequiredArgsConstructor
public class FeePersistenceAdapter implements FeeRepositoryPort {

    private final FeeJpaRepository jpaRepo;

    @Override
    public Fee save(Fee fee) {
        // Flush để id + timestamps do Hibernate sinh có mặt trong domain trả về.
        return toDomain(jpaRepo.saveAndFlush(toEntity(fee)));
    }

    @Override
    public List<Fee> saveAll(List<Fee> list) {
        List<FeeJpaEntity> entities = list.stream().map(this::toEntity).toList();
        return jpaRepo.saveAllAndFlush(entities).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Fee> findById(UUID id) {
        return jpaRepo.findById(id).map(this::toDomain);
    }

    @Override
    public List<Fee> findUnpaidByPatient(UUID patientId) {
        return jpaRepo.findUnpaidByPatient(patientId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Fee> findByInvoice(UUID invoiceId) {
        return jpaRepo.findByInvoiceId(invoiceId).stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsBySource(FeeType feeType, UUID sourceRefId) {
        return jpaRepo.existsByFeeTypeAndSourceRefId(feeType, sourceRefId);
    }

    // ---- map entity ↔ domain (thủ công, không MapStruct) ----

    private Fee toDomain(FeeJpaEntity e) {
        return Fee.restore(
                e.getFeeId(), e.getPatientId(), e.getRecordId(), e.getDepartmentId(), e.getSourceRefId(),
                e.getFeeType(), e.getIncurredDate(), e.getAmount(), e.isPaid(), e.getInvoiceId(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private FeeJpaEntity toEntity(Fee f) {
        return FeeJpaEntity.builder()
                .feeId(f.getFeeId())
                .patientId(f.getPatientId())
                .recordId(f.getRecordId())
                .departmentId(f.getDepartmentId())
                .sourceRefId(f.getSourceRefId())
                .feeType(f.getFeeType())
                .incurredDate(f.getIncurredDate())
                .amount(f.getAmount())
                .isPaid(f.isPaid())
                .invoiceId(f.getInvoiceId())
                // Lưu mới: Hibernate gán createdAt; cập nhật: giữ nguyên createdAt của bản ghi cũ.
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .build();
    }
}
