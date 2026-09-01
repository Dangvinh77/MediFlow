package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.pharmacy.application.port.out.DrugRepositoryPort;
import com.mediflow.pharmacy.domain.model.Drug;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.DrugJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.DrugJpaEntityRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link DrugRepositoryPort} — implement ngoài {@code application}, đóng gói
 * Spring Data JPA. {@code application} chỉ thấy port (không biết JPA tồn tại); mọi chuyện
 * đổi entity ↔ domain model đều nằm ở đây.
 *
 * <p>Khóa ghi {@code findByIdForUpdate} đi thẳng xuống
 * {@link DrugJpaEntityRepository#findByIdForUpdate(UUID)} (PESSIMISTIC_WRITE) — bản chất
 * khóa là quyết định của tầng hạ tầng, port chỉ nói lên nhu cầu "đưa tôi bản khóa".
 */
@Component
@RequiredArgsConstructor
public class DrugPersistenceAdapter implements DrugRepositoryPort {

    private final DrugJpaEntityRepository jpaRepo;

    @Override
    public Drug save(Drug drug) {
        // Lưu mới: entity chưa có id (drugId == null) → lưu xong Hibernate sinh id + timestamps.
        DrugJpaEntity entity = toEntity(drug);
        DrugJpaEntity saved = jpaRepo.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Drug> findById(UUID id) {
        return jpaRepo.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Drug> findByIdForUpdate(UUID id) {
        return jpaRepo.findByIdForUpdate(id).map(this::toDomain);
    }

    @Override
    public PageResult<Drug> search(String keyword, PageQuery query) {
        Pageable pageable = PageRequest.of(query.page(), query.size());
        Page<DrugJpaEntity> page = jpaRepo.search(keyword, pageable);
        return PageResult.of(
                page.getContent().stream().map(this::toDomain).toList(),
                page.getTotalElements(),
                query.page(),
                query.size());
    }

    // ---- map entity ↔ domain (thủ công, không MapStruct — quy tắc domain không lọt ra ngoài) ----

    private Drug toDomain(DrugJpaEntity e) {
        return Drug.restore(
                e.getDrugId(), e.getDrugName(), e.getActiveIngredient(), e.getUnit(),
                e.getPrice(), e.getStockQuantity(), e.getExpiryDate(), e.getManufacturer(),
                e.getLowStockThreshold(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private DrugJpaEntity toEntity(Drug d) {
        return DrugJpaEntity.builder()
                .drugId(d.getDrugId())
                .drugName(d.getDrugName())
                .activeIngredient(d.getActiveIngredient())
                .unit(d.getUnit())
                .price(d.getPrice())
                .stockQuantity(d.getStockQuantity())
                .expiryDate(d.getExpiryDate())
                .manufacturer(d.getManufacturer())
                .lowStockThreshold(d.getLowStockThreshold())
                // createdAt/updatedAt để Hibernate tự sinh khi save (CREATE/UPDATE timestamps)
                .build();
    }
}
