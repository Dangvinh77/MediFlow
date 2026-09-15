package com.mediflow.report.application.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.mediflow.report.application.dto.response.DailyReportDTO;
import com.mediflow.report.application.dto.response.MonthlyReportDTO;
import com.mediflow.report.application.dto.response.TopMedicineDTO;
import com.mediflow.report.domain.model.DailyVisitReport;
import com.mediflow.report.domain.model.MonthlyRevenueReport;
import com.mediflow.report.domain.model.TopMedicineSummary;

/** Maps domain read models to immutable application response records. */
@Mapper(componentModel = "spring")
public interface ReportDtoMapper {

    DailyReportDTO toDailyDto(DailyVisitReport report);

    @Mapping(target = "dailyDetails", source = "dailyDetails")
    MonthlyReportDTO toMonthlyDto(MonthlyRevenueReport report, List<DailyReportDTO> dailyDetails);

    TopMedicineDTO toTopMedicineDto(TopMedicineSummary summary);

    List<TopMedicineDTO> toTopMedicineDtoList(List<TopMedicineSummary> summaries);
}
