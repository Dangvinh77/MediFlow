package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.response.OutpatientReportDTO;
import com.mediflow.report.application.dto.response.OutpatientReportDTO.Period;
import com.mediflow.report.application.exception.ReportPeriodValidationException;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.in.ReadOutpatientReportUseCase;
import com.mediflow.report.application.port.out.OutpatientReportReadPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/** One repeatable local snapshot of coverage/evidence; no producer REST/SQL or all-five publication. */
public class OutpatientReportReadService implements ReadOutpatientReportUseCase {
    private final OutpatientReportReadPort store;private final ZoneId zone;private final Clock clock;
    public OutpatientReportReadService(OutpatientReportReadPort store,ZoneId zone,Clock clock){this.store=store;this.zone=zone;this.clock=clock;}
    @Override @Transactional(transactionManager = "reportTransactionManager", readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public OutpatientReportDTO daily(LocalDate from,LocalDate to,UUID departmentId){return query(from,to,departmentId,false);}
    @Override @Transactional(transactionManager = "reportTransactionManager", readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public OutpatientReportDTO monthly(int year,UUID departmentId){
        if(year<2000||year>2100)throw new ReportPeriodValidationException();
        return query(LocalDate.of(year,1,1),LocalDate.of(year,12,31),departmentId,true);
    }
    private OutpatientReportDTO query(LocalDate from,LocalDate to,UUID department,boolean monthly){
        if(from==null||to==null||to.isBefore(from)||from.getYear()<2000||to.getYear()>2100
                ||ChronoUnit.DAYS.between(from,to)>=366)throw new ReportPeriodValidationException();
        var evidence=store.load(from,to,department,zone.getId(),"VND").orElseThrow(ReportProjectionUnavailableException::new);
        var coverage=evidence.coverage();
        if(coverage==null||!coverage.covers(from,to,department,zone.getId(),"VND",clock.instant())||evidence.pendingOrInvalid()
                ||!coverage.evidenceFingerprint().equals(evidence.currentFingerprint()))throw new ReportProjectionUnavailableException();
        var days=new TreeMap<LocalDate,OutpatientReportReadPort.Day>();
        for(var day:evidence.days()){
            if(day==null||day.date()==null||day.date().isBefore(from)||day.date().isAfter(to)||days.putIfAbsent(day.date(),day)!=null)
                throw new ReportProjectionUnavailableException();
        }
        var periods=new ArrayList<Period>();
        try{
            if(!monthly)for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1))periods.add(period(d,d,days));
            else for(int m=1;m<=12;m++){var month=YearMonth.of(from.getYear(),m);periods.add(period(month.atDay(1),month.atEndOfMonth(),days));}
        }catch(ArithmeticException|IllegalArgumentException invalid){throw new ReportProjectionUnavailableException();}
        return new OutpatientReportDTO(from,to,department,"VND",zone.getId(),coverage.coverageId(),coverage.sourceObservedAt(),
                coverage.reconciledAt(),true,List.copyOf(periods));
    }
    private Period period(LocalDate from,LocalDate to,TreeMap<LocalDate,OutpatientReportReadPort.Day> days){
        long visits=0,lab=0,rx=0,units=0;BigDecimal cash=new BigDecimal("0.00"),refund=new BigDecimal("0.00");
        for(var d:days.subMap(from,true,to,true).values()){
            if(d.completedVisits()<0||d.labTests()<0||d.dispensedPrescriptions()<0||d.dispensedUnits()<0
                    ||d.cashReceived()==null||d.refunds()==null||d.cashReceived().signum()<0||d.refunds().signum()<0)
                throw new IllegalArgumentException("Invalid outpatient values");
            visits=Math.addExact(visits,d.completedVisits());lab=Math.addExact(lab,d.labTests());
            rx=Math.addExact(rx,d.dispensedPrescriptions());units=Math.addExact(units,d.dispensedUnits());
            cash=cash.add(d.cashReceived()).setScale(2,RoundingMode.UNNECESSARY);refund=refund.add(d.refunds()).setScale(2,RoundingMode.UNNECESSARY);
        }
        if(cash.precision()>19||refund.precision()>19)throw new ArithmeticException("Money overflow");
        return new Period(from,to,visits,lab,rx,units,cash,refund,cash.subtract(refund));
    }
}
