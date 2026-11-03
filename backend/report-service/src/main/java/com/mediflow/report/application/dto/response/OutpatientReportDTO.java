package com.mediflow.report.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record OutpatientReportDTO(LocalDate fromDate,LocalDate toDate,UUID departmentId,String currency,String zoneId,
        UUID coverageId,Instant sourceObservedAt,Instant reconciledAt,boolean covered,List<Period> periods, boolean syntheticData){
    /** Source compatibility for existing producers/tests; reader supplies explicit provenance. */
    public OutpatientReportDTO(LocalDate fromDate,LocalDate toDate,UUID departmentId,String currency,String zoneId,
            UUID coverageId,Instant sourceObservedAt,Instant reconciledAt,boolean covered,List<Period> periods) {
        this(fromDate,toDate,departmentId,currency,zoneId,coverageId,sourceObservedAt,reconciledAt,covered,periods,false);
    }
    public record Period(LocalDate fromDate,LocalDate toDate,long completedVisits,long labTests,
            long dispensedPrescriptions,long dispensedUnits,BigDecimal cashReceived,BigDecimal refunds,BigDecimal netCash){ }
}
