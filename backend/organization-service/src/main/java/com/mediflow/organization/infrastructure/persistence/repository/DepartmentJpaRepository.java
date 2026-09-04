package com.mediflow.organization.infrastructure.persistence.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.organization.infrastructure.persistence.entity.DepartmentEntity;

import java.util.UUID;

/**
 * Spring Data JPA Repository.
 *
 * Đây là nơi Infrastructure giao tiếp trực tiếp
 * với database thông qua Spring Data JPA.
 */
public interface DepartmentJpaRepository
        extends JpaRepository<DepartmentEntity, UUID> {

    /**
     * Kiểm tra abbreviation đã tồn tại chưa.
     *
     * Spring Data JPA tự tạo implementation
     * dựa trên tên method.
     *
     * Tương đương về mặt ý nghĩa:
     *
     * SELECT EXISTS (
     *     SELECT 1
     *     FROM department
     *     WHERE abbreviation = ?
     * );
     */
    boolean existsByAbbreviation(String abbreviation);
}