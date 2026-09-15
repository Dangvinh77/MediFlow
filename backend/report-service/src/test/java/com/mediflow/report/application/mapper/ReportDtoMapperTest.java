package com.mediflow.report.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.mediflow.report.application.dto.response.DailyReportDTO;
import com.mediflow.report.application.dto.response.MonthlyReportDTO;
import com.mediflow.report.application.dto.response.TopMedicineDTO;
import com.mediflow.report.domain.model.DailyVisitReport;
import com.mediflow.report.domain.model.MonthlyRevenueReport;
import com.mediflow.report.domain.model.TopMedicineSummary;

class ReportDtoMapperTest {

    private final ReportDtoMapper mapper = Mappers.getMapper(ReportDtoMapper.class);

    @Test
    void toDailyDto_preservesNullDepartmentAndMoney() {
        DailyVisitReport report = DailyVisitReport.initialize(LocalDate.of(2026, 9, 1), null);
        report.incrementVisits(2);
        report.addRevenue(new BigDecimal("12.30"));

        DailyReportDTO dto = mapper.toDailyDto(report);

        assertThat(dto.departmentId()).isNull();
        assertThat(dto.visitCount()).isEqualTo(2);
        assertThat(dto.revenue()).isEqualByComparingTo("12.30");
    }

    @Test
    void toMonthlyDto_keepsDailyDetailsImmutable() {
        MonthlyRevenueReport report = MonthlyRevenueReport.initialize(9, 2026, UUID.randomUUID());
        List<DailyReportDTO> details = List.of(
                new DailyReportDTO(LocalDate.of(2026, 9, 1), report.getDepartmentId(), 1, 0, 0,
                        new BigDecimal("1.00")));

        MonthlyReportDTO dto = mapper.toMonthlyDto(report, details);

        assertThat(dto.dailyDetails()).containsExactlyElementsOf(details);
        assertThatThrownBy(() -> dto.dailyDetails().add(details.get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toTopMedicineDto_mapsSummary() {
        UUID drugId = UUID.randomUUID();

        TopMedicineDTO dto = mapper.toTopMedicineDto(new TopMedicineSummary(drugId, "A", 4));

        assertThat(dto.drugId()).isEqualTo(drugId);
        assertThat(dto.totalQuantity()).isEqualTo(4);
    }
}
