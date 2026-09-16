package com.mediflow.report.infrastructure.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mediflow.report.infrastructure.persistence.DrugStatisticJpaEntity;

/** Native PostgreSQL repository for drug statistics and grouped top queries. */
public interface DrugStatisticJpaRepository extends JpaRepository<DrugStatisticJpaEntity, UUID> {

    @Modifying
    @Query(value = "INSERT INTO DRUG_STATISTIC (statistic_id, drug_id, drug_name, report_date, department_id) "
            + "VALUES (:statisticId, :drugId, :drugName, :reportDate, :departmentId) "
            + "ON CONFLICT (drug_id, report_date, department_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("statisticId") UUID statisticId, @Param("drugId") UUID drugId,
                       @Param("drugName") String drugName, @Param("reportDate") LocalDate reportDate,
                       @Param("departmentId") UUID departmentId);

    @Query(value = "SELECT * FROM DRUG_STATISTIC WHERE drug_id = :drugId AND report_date = :reportDate "
            + "AND (department_id = :departmentId OR (:departmentId IS NULL AND department_id IS NULL)) "
            + "FOR UPDATE", nativeQuery = true)
    Optional<DrugStatisticJpaEntity> findForUpdate(@Param("drugId") UUID drugId,
                                                   @Param("reportDate") LocalDate reportDate,
                                                   @Param("departmentId") UUID departmentId);

    interface TopMedicineProjection {
        UUID getDrugId();
        String getDrugName();
        Long getTotalQuantity();
    }

    @Query(value = "SELECT drug_id AS drugId, "
            + "(array_agg(drug_name ORDER BY report_date DESC, statistic_id DESC))[1] AS drugName, "
            + "SUM(dispensed_quantity)::bigint AS totalQuantity "
            + "FROM DRUG_STATISTIC WHERE report_date BETWEEN :fromDate AND :toDate "
            + "AND (department_id = :departmentId OR (:departmentId IS NULL AND department_id IS NULL)) "
            + "GROUP BY drug_id ORDER BY totalQuantity DESC, drug_id ASC LIMIT :limit", nativeQuery = true)
    List<TopMedicineProjection> topMedicines(@Param("fromDate") LocalDate fromDate,
                                             @Param("toDate") LocalDate toDate,
                                             @Param("departmentId") UUID departmentId,
                                             @Param("limit") int limit);
}
