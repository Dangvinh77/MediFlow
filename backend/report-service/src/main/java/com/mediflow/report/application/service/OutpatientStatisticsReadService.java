package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.response.OutpatientStatisticsDTO;
import com.mediflow.report.application.exception.ReportPeriodValidationException;
import com.mediflow.report.application.port.in.ReadOutpatientStatisticsUseCase;
import com.mediflow.report.application.port.out.OutpatientStatisticsReadPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisional daily and per-department outpatient figures for charts. Every requested day is
 * present (zero when no fact). Unlike {@link OutpatientReportReadService} it needs no accepted
 * coverage, so the response always says {@code reconciled=false}.
 */
public class OutpatientStatisticsReadService implements ReadOutpatientStatisticsUseCase {
    private static final String CURRENCY = "VND";
    private final OutpatientStatisticsReadPort store;
    private final ZoneId zone;

    public OutpatientStatisticsReadService(OutpatientStatisticsReadPort store, ZoneId zone) {
        this.store = store;
        this.zone = zone;
    }

    @Override
    @Transactional(transactionManager = "reportTransactionManager", readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OutpatientStatisticsDTO statistics(LocalDate from, LocalDate to, UUID departmentId) {
        if (from == null || to == null || to.isBefore(from) || from.getYear() < 2000 || to.getYear() > 2100
                || ChronoUnit.DAYS.between(from, to) >= 366) throw new ReportPeriodValidationException();
        var days = new TreeMap<LocalDate, Sum>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) days.put(d, new Sum());
        var departments = new TreeMap<UUID, Sum>();
        var total = new Sum();
        for (var row : store.load(from, to, departmentId, zone.getId(), CURRENCY)) {
            if (row.date() == null || row.amount() == null || row.amount().signum() < 0 || !days.containsKey(row.date())
                    || (departmentId != null && !departmentId.equals(row.departmentId())))
                throw new IllegalStateException("Invalid outpatient statistics row");
            days.get(row.date()).add(row);
            if (row.departmentId() != null) departments.computeIfAbsent(row.departmentId(), id -> new Sum()).add(row);
            total.add(row);
        }
        return new OutpatientStatisticsDTO(from, to, departmentId, CURRENCY, zone.getId(), false,
                new OutpatientStatisticsDTO.Totals(total.visits, total.exam, total.lab, total.rx, total.other, total.refunds),
                days.entrySet().stream().map(e -> e.getValue().day(e.getKey())).toList(),
                departments.entrySet().stream().map(e -> e.getValue().department(e.getKey())).toList());
    }

    private static final class Sum {
        long visits;
        BigDecimal exam = money(), lab = money(), rx = money(), other = money(), refunds = money();

        void add(OutpatientStatisticsReadPort.Row row) {
            switch (row.kind()) {
                case "VISITS" -> visits = Math.addExact(visits, row.amount().longValueExact());
                case "EXAM" -> exam = exam.add(row.amount());
                case "LAB_TEST" -> lab = lab.add(row.amount());
                case "PRESCRIPTION" -> rx = rx.add(row.amount());
                case "UNCLASSIFIED" -> other = other.add(row.amount());
                case "REFUND" -> refunds = refunds.add(row.amount());
                default -> throw new IllegalStateException("Unsupported outpatient statistics kind");
            }
        }

        OutpatientStatisticsDTO.Day day(LocalDate date) {
            return new OutpatientStatisticsDTO.Day(date, visits, exam, lab, rx, other, refunds);
        }

        OutpatientStatisticsDTO.Department department(UUID id) {
            return new OutpatientStatisticsDTO.Department(id, visits, exam, lab, rx, other, refunds);
        }

        private static BigDecimal money() { return new BigDecimal("0.00"); }
    }
}
