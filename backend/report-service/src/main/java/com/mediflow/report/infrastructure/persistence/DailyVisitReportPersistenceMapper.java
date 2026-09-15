package com.mediflow.report.infrastructure.persistence;

import com.mediflow.report.domain.model.DailyVisitReport;

/** Explicit mapper between the daily domain model and its JPA row. */
public final class DailyVisitReportPersistenceMapper {

    private DailyVisitReportPersistenceMapper() {}

    public static DailyVisitReport toDomain(DailyVisitReportJpaEntity entity) {
        return DailyVisitReport.restore(entity.getReportId(), entity.getReportDate(), entity.getDepartmentId(),
                entity.getVisitCount(), entity.getLabCount(), entity.getPrescriptionCount(), entity.getRevenue(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public static DailyVisitReportJpaEntity toEntity(DailyVisitReport report) {
        return DailyVisitReportJpaEntity.builder()
                .reportId(report.getReportId())
                .reportDate(report.getReportDate())
                .departmentId(report.getDepartmentId())
                .visitCount(report.getVisitCount())
                .labCount(report.getLabCount())
                .prescriptionCount(report.getPrescriptionCount())
                .revenue(report.getRevenue())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
