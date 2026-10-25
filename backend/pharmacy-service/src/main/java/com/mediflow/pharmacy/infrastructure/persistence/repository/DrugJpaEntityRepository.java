package com.mediflow.pharmacy.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.pharmacy.infrastructure.persistence.jpaentity.DrugJpaEntity;

import jakarta.persistence.LockModeType;

public interface DrugJpaEntityRepository extends JpaRepository<DrugJpaEntity, UUID> {
// Khóa bản ghi thuốc khi đọc để transaction hiện tại có quyền cập nhật độc quyền.
// Transaction khác muốn sửa cùng bản ghi phải chờ lock được giải phóng.
   @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DrugJpaEntity d WHERE d.drugId = :id")
    Optional<DrugJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT d FROM DrugJpaEntity d
            WHERE LOWER(d.drugName) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
               OR LOWER(d.activeIngredient) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
            """)
    Page<DrugJpaEntity> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            SELECT d FROM DrugJpaEntity d
            WHERE (LOWER(d.drugName) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\'
                OR LOWER(d.activeIngredient) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\\')
            AND (:filter = 'ALL'
                OR (:filter = 'IN_STOCK' AND d.stockQuantity > 0)
                OR (:filter = 'LOW_STOCK' AND d.stockQuantity > 0 AND d.stockQuantity <= d.lowStockThreshold)
                OR (:filter = 'OUT_OF_STOCK' AND d.stockQuantity = 0)
                OR (:filter = 'EXPIRED' AND d.expiryDate < :today)
                OR (:filter = 'EXPIRING_SOON' AND d.expiryDate >= :today AND d.expiryDate <= :soon))
            """)
    Page<DrugJpaEntity> searchFiltered(@Param("keyword") String keyword, @Param("filter") String filter,
            @Param("today") java.time.LocalDate today, @Param("soon") java.time.LocalDate soon,
            Pageable pageable);
}
