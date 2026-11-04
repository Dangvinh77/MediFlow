package com.mediflow.report.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Report-owned outpatient facts grouped by business date and department; no producer access. */
public interface OutpatientStatisticsReadPort {
    /** One row per (date, department, kind). kind: VISITS, EXAM, LAB_TEST, PRESCRIPTION, UNCLASSIFIED, REFUND. */
    List<Row> load(LocalDate from, LocalDate to, UUID departmentId, String zoneId, String currency);

    record Row(LocalDate date, UUID departmentId, String kind, BigDecimal amount) { }
}
