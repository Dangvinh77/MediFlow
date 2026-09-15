package com.mediflow.report.infrastructure.persistence;

import com.mediflow.report.domain.model.DrugStatistic;

/** Explicit mapper between the medicine statistic domain model and its JPA row. */
public final class DrugStatisticPersistenceMapper {

    private DrugStatisticPersistenceMapper() {}

    public static DrugStatistic toDomain(DrugStatisticJpaEntity entity) {
        return DrugStatistic.restore(entity.getStatisticId(), entity.getDrugId(), entity.getDrugName(),
                entity.getReportDate(), entity.getDepartmentId(), entity.getDispensedQuantity(),
                entity.getUpdatedAt());
    }

    public static DrugStatisticJpaEntity toEntity(DrugStatistic statistic) {
        return DrugStatisticJpaEntity.builder()
                .statisticId(statistic.getStatisticId())
                .drugId(statistic.getDrugId())
                .drugName(statistic.getDrugName())
                .reportDate(statistic.getReportDate())
                .departmentId(statistic.getDepartmentId())
                .dispensedQuantity(statistic.getDispensedQuantity())
                .updatedAt(statistic.getUpdatedAt())
                .build();
    }
}
