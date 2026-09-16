package com.mediflow.report.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.report.infrastructure.persistence.MonthlyRevenueReportJpaEntity;

/** Native PostgreSQL repository for monthly revenue projections. */
public interface MonthlyRevenueReportJpaRepository extends JpaRepository<MonthlyRevenueReportJpaEntity, UUID> {

    @Modifying
    @Query(value = "INSERT INTO MONTHLY_REVENUE_REPORT (report_id, month, year, department_id) "
            + "VALUES (:reportId, :month, :year, :departmentId) "
            + "ON CONFLICT (year, month, department_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("reportId") UUID reportId, @Param("month") int month,
                       @Param("year") int year, @Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM MONTHLY_REVENUE_REPORT WHERE month = :month AND year = :year "
            + "AND (department_id = :departmentId OR (:departmentId IS NULL AND department_id IS NULL))",
            nativeQuery = true)
    Optional<MonthlyRevenueReportJpaEntity> findByScope(@Param("month") int month, @Param("year") int year,
                                                       @Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM MONTHLY_REVENUE_REPORT WHERE month = :month AND year = :year "
            + "AND (department_id = :departmentId OR (:departmentId IS NULL AND department_id IS NULL)) "
            + "FOR UPDATE", nativeQuery = true)
    Optional<MonthlyRevenueReportJpaEntity> findForUpdate(@Param("month") int month, @Param("year") int year,
                                                          @Param("departmentId") UUID departmentId);
}
