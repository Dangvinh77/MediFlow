package com.mediflow.report.application.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.dto.response.DailyReportDTO;
import com.mediflow.report.application.dto.response.MonthlyReportDTO;
import com.mediflow.report.application.dto.response.TopMedicineDTO;
import com.mediflow.report.application.exception.ReportDateRangeException;
import com.mediflow.report.application.mapper.ReportDtoMapper;
import com.mediflow.report.application.port.in.ReadReportUseCase;
import com.mediflow.report.application.port.out.DailyVisitReportRepositoryPort;
import com.mediflow.report.application.port.out.DrugStatisticRepositoryPort;
import com.mediflow.report.application.port.out.MonthlyRevenueReportRepositoryPort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.DailyVisitReport;
import com.mediflow.report.domain.model.MonthlyRevenueReport;

import lombok.RequiredArgsConstructor;

/** Application orchestration for stable, zero-filled report read models. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportApplicationService implements ReadReportUseCase {

    static final int DEFAULT_TOP_LIMIT = 10;
    static final int MAX_TOP_LIMIT = 50;

    private final DailyVisitReportRepositoryPort dailyReports;
    private final MonthlyRevenueReportRepositoryPort monthlyReports;
    private final DrugStatisticRepositoryPort drugStatistics;
    private final ReportDtoMapper mapper;

    @Override
    public DailyReportDTO daily(LocalDate date, UUID departmentId) {
        if (date == null) {
            throw new ReportRuleException("REPORT_DATE_REQUIRED", "Ngày báo cáo là bắt buộc");
        }
        DailyVisitReport report = dailyReports.find(date, departmentId)
                .orElseGet(() -> DailyVisitReport.initialize(date, departmentId));
        return mapper.toDailyDto(report);
    }

    @Override
    public MonthlyReportDTO monthly(int month, int year, UUID departmentId) {
        validateMonthYear(month, year);
        MonthlyRevenueReport aggregate = monthlyReports.find(year, month, departmentId)
                .orElseGet(() -> MonthlyRevenueReport.initialize(month, year, departmentId));

        YearMonth calendarMonth = YearMonth.of(year, month);
        LocalDate firstDay = calendarMonth.atDay(1);
        LocalDate lastDay = calendarMonth.atEndOfMonth();
        List<DailyVisitReport> rows = dailyReports.findRange(firstDay, lastDay, departmentId);
        Map<LocalDate, DailyVisitReport> byDate = rows == null ? Map.of() : rows.stream()
                .collect(Collectors.toMap(DailyVisitReport::getReportDate, report -> report,
                        (first, second) -> second, HashMap::new));

        List<DailyReportDTO> dailyDetails = calendarDates(firstDay, lastDay).stream()
                .map(date -> mapper.toDailyDto(byDate.getOrDefault(date,
                        DailyVisitReport.initialize(date, departmentId))))
                .toList();
        return mapper.toMonthlyDto(aggregate, dailyDetails);
    }

    @Override
    public List<TopMedicineDTO> topMedicines(LocalDate fromDate, LocalDate toDate,
                                             UUID departmentId, int limit) {
        validateRange(fromDate, toDate);
        int effectiveLimit = normalizeLimit(limit);
        return mapper.toTopMedicineDtoList(
                drugStatistics.topMedicines(fromDate, toDate, departmentId, effectiveLimit));
    }

    private static List<LocalDate> calendarDates(LocalDate firstDay, LocalDate lastDay) {
        long days = lastDay.toEpochDay() - firstDay.toEpochDay() + 1;
        return java.util.stream.LongStream.range(0, days)
                .mapToObj(firstDay::plusDays)
                .toList();
    }

    private static void validateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new ReportDateRangeException(
                    "Khoảng ngày báo cáo phải có đủ hai đầu mút và fromDate không được sau toDate");
        }
    }

    private static void validateMonthYear(int month, int year) {
        if (month < 1 || month > 12) {
            throw new ReportRuleException("REPORT_MONTH_INVALID", "Tháng báo cáo phải từ 1 đến 12");
        }
        if (year < 2000 || year > 2100) {
            throw new ReportRuleException("REPORT_YEAR_INVALID",
                    "Năm báo cáo phải trong khoảng từ 2000 đến 2100");
        }
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_TOP_LIMIT;
        }
        return Math.min(limit, MAX_TOP_LIMIT);
    }
}
