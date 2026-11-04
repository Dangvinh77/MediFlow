package com.mediflow.report.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Provisional outpatient operations from Report-owned projections: visits plus gross receipts by
 * service line. Not reconciled coverage ({@code reconciled=false}); never a substitute for
 * {@link OutpatientReportDTO}. {@code unclassifiedRevenue} holds receipts with no recorded line.
 */
public record OutpatientStatisticsDTO(LocalDate fromDate, LocalDate toDate, UUID departmentId, String currency,
        String zoneId, boolean reconciled, Totals totals, List<Day> days, List<Department> departments) {

    public record Totals(long completedVisits, BigDecimal examRevenue, BigDecimal labRevenue,
            BigDecimal prescriptionRevenue, BigDecimal unclassifiedRevenue, BigDecimal refunds) { }

    public record Day(LocalDate date, long completedVisits, BigDecimal examRevenue, BigDecimal labRevenue,
            BigDecimal prescriptionRevenue, BigDecimal unclassifiedRevenue, BigDecimal refunds) { }

    public record Department(UUID departmentId, long completedVisits, BigDecimal examRevenue, BigDecimal labRevenue,
            BigDecimal prescriptionRevenue, BigDecimal unclassifiedRevenue, BigDecimal refunds) { }
}
