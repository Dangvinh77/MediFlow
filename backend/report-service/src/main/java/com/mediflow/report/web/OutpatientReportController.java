package com.mediflow.report.web;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.common.security.JwtClaims;
import com.mediflow.report.application.dto.response.OutpatientReportDTO;
import com.mediflow.report.application.port.in.ReadOutpatientReportUseCase;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

@RestController @RequestMapping("/api/v1/reports/outpatient") @Validated @Profile("mvp-outpatient | outpatient-mvp")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2","mediflow.report.outpatient-read.enabled"},havingValue="true")
public class OutpatientReportController {
    private final ReadOutpatientReportUseCase reader;
    public OutpatientReportController(ReadOutpatientReportUseCase reader){this.reader=reader;}
    @GetMapping("/daily") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<OutpatientReportDTO> daily(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate toDate,@RequestParam(required=false)UUID departmentId,
            HttpServletRequest request){return ApiResponse.ok(reader.daily(fromDate,toDate,departmentId),correlation(request));}
    @GetMapping("/monthly") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApiResponse<OutpatientReportDTO> monthly(@RequestParam @Min(2000) @Max(2100)int year,
            @RequestParam(required=false)UUID departmentId,HttpServletRequest request){
        return ApiResponse.ok(reader.monthly(year,departmentId),correlation(request));
    }
    private String correlation(HttpServletRequest request){Object value=request.getAttribute(JwtClaims.CORRELATION_ID);
        return value instanceof String s?s:null;}
}
