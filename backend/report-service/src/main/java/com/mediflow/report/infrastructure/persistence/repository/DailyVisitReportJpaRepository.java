package com.mediflow.report.infrastructure.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.report.infrastructure.persistence.DailyVisitReportJpaEntity;

/** Native PostgreSQL repository for the daily projection. */
public interface DailyVisitReportJpaRepository extends JpaRepository<DailyVisitReportJpaEntity, UUID> {

    @Modifying
    @Query(value = "INSERT INTO DAILY_VISIT_REPORT (report_id, report_date, department_id) "
            + "VALUES (:reportId, :reportDate, :departmentId) "
            + "ON CONFLICT (report_date, department_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("reportId") UUID reportId, @Param("reportDate") LocalDate reportDate,
                       @Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM DAILY_VISIT_REPORT WHERE report_date = :reportDate "
            + "AND (department_id = :departmentId OR (:departmentId IS NULL AND department_id IS NULL)) "
            + "FOR UPDATE", nativeQuery = true)
    Optional<DailyVisitReportJpaEntity> findForUpdate(@Param("reportDate") LocalDate reportDate,
                                                      @Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM DAILY_VISIT_REPORT WHERE report_date = :reportDate "
            + "AND (department_id = :departmentId OR (:departmentId IS NULL AND department_id IS NULL))",
            nativeQuery = true)
    Optional<DailyVisitReportJpaEntity> findByScope(@Param("reportDate") LocalDate reportDate,
                                                   @Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM DAILY_VISIT_REPORT WHERE report_date BETWEEN :fromDate AND :toDate "
            + "AND (department_id = :departmentId OR (:departmentId IS NULL AND department_id IS NULL)) "
            + "ORDER BY report_date", nativeQuery = true)
    List<DailyVisitReportJpaEntity> findRangeByScope(@Param("fromDate") LocalDate fromDate,
                                                     @Param("toDate") LocalDate toDate,
                                                     @Param("departmentId") UUID departmentId);
}
