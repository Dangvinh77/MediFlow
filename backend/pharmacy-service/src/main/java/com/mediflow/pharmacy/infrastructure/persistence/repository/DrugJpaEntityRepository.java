package com.mediflow.pharmacy.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.DrugJpaEntity;

import jakarta.persistence.LockModeType;

public interface DrugJpaEntityRepository extends JpaRepository<DrugJpaEntity, UUID> {
// Khóa bản ghi thuốc khi đọc để transaction hiện tại có quyền cập nhật độc quyền.
// Transaction khác muốn sửa cùng bản ghi phải chờ lock được giải phóng.
   @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DrugJpaEntity d WHERE d.drugId = :id")
    Optional<DrugJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT d FROM DrugJpaEntity d
            WHERE LOWER(d.drugName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    Page<DrugJpaEntity> search(@Param("keyword") String keyword, Pageable pageable);
}
