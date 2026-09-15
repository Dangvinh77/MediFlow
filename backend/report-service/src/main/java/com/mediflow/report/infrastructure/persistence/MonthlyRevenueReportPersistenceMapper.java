package com.mediflow.report.infrastructure.persistence;

import com.mediflow.report.domain.model.MonthlyRevenueReport;

/** Explicit mapper between the monthly domain model and its JPA row. */
public final class MonthlyRevenueReportPersistenceMapper {

    private MonthlyRevenueReportPersistenceMapper() {}

    public static MonthlyRevenueReport toDomain(MonthlyRevenueReportJpaEntity entity) {
        return MonthlyRevenueReport.restore(entity.getReportId(), entity.getMonth(), entity.getYear(),
                entity.getDepartmentId(), entity.getTotalRevenue(), entity.getInvoiceCount(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public static MonthlyRevenueReportJpaEntity toEntity(MonthlyRevenueReport report) {
        return MonthlyRevenueReportJpaEntity.builder()
                .reportId(report.getReportId())
                .month(report.getMonth())
                .year(report.getYear())
                .departmentId(report.getDepartmentId())
                .totalRevenue(report.getTotalRevenue())
                .invoiceCount(report.getInvoiceCount())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
