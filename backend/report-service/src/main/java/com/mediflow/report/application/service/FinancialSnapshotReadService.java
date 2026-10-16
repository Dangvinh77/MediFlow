package com.mediflow.report.application.service;

import java.time.LocalDate;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import com.mediflow.report.application.dto.response.FinancialSnapshotReportDTO;
import com.mediflow.report.application.dto.response.FinancialSnapshotReportDTO.Period;
import com.mediflow.report.application.exception.ReportPeriodValidationException;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.application.port.in.ReadFinancialSnapshotUseCase;
import com.mediflow.report.application.port.out.FinancialSnapshotReadPort;
import com.mediflow.report.domain.model.FinancialAmounts;

/** Requires an explicit complete row for every day/scope: absent closing stock cannot be zero-filled. */
@Transactional(transactionManager = "reportTransactionManager", readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=8)
public class FinancialSnapshotReadService implements ReadFinancialSnapshotUseCase {
    private final FinancialSnapshotReadPort store;
    private final ZoneId zone;
    private final String currency;
    private final Clock clock;
    public FinancialSnapshotReadService(FinancialSnapshotReadPort store, ZoneId zone, String currency) {
        this(store,zone,currency,Clock.systemUTC());
    }
    public FinancialSnapshotReadService(FinancialSnapshotReadPort store, ZoneId zone, String currency, Clock clock) {
        if (currency==null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("One exact report currency required");
        this.store=store; this.zone=zone; this.currency=currency; this.clock=clock;
    }
    @Override public FinancialSnapshotReportDTO daily(LocalDate from, LocalDate to, UUID departmentId) {
        return query(from,to,departmentId,false);
    }
    @Override public FinancialSnapshotReportDTO monthly(int year, UUID departmentId) {
        if (year<2000 || year>2100) throw new ReportPeriodValidationException();
        return query(LocalDate.of(year,1,1),LocalDate.of(year,12,31),departmentId,true);
    }
    private FinancialSnapshotReportDTO query(LocalDate from, LocalDate to, UUID departmentId, boolean monthly) {
        if (from==null || to==null || to.isBefore(from) || from.getYear()<2000 || to.getYear()>2100
                || ChronoUnit.DAYS.between(from,to)>=366) throw new ReportPeriodValidationException();
        var publication=store.publication(currency,zone.getId())
                .filter(value -> value.covers(from,to,currency,zone.getId()))
                .filter(value -> !value.publishedAt().isAfter(clock.instant()))
                .orElseThrow(ReportProjectionUnavailableException::new);
        var days=new TreeMap<LocalDate,FinancialAmounts>();
        for (var value:store.read(publication.generationId(),from,to,departmentId)) {
            if (value==null || value.date()==null || value.amounts()==null || value.date().isBefore(from)
                    || value.date().isAfter(to) || days.putIfAbsent(value.date(),value.amounts())!=null) {
                throw new ReportProjectionUnavailableException();
            }
        }
        if (days.size()!=ChronoUnit.DAYS.between(from,to)+1) throw new ReportProjectionUnavailableException();
        var periods=new ArrayList<Period>();
        try {
            if (!monthly) days.forEach((date,value) -> periods.add(period(date,date,value)));
            else for (int month=1;month<=12;month++) {
                var calendar=YearMonth.of(from.getYear(),month);
                var values=days.subMap(calendar.atDay(1),true,calendar.atEndOfMonth(),true).values().iterator();
                var total=values.next();
                while(values.hasNext()) total=total.through(values.next());
                periods.add(period(calendar.atDay(1),calendar.atEndOfMonth(),total));
            }
        } catch (IllegalArgumentException exception) { throw new ReportProjectionUnavailableException(); }
        return new FinancialSnapshotReportDTO(from,to,departmentId,currency,zone.getId(),publication.generationId(),
                publication.sourceObservedAt(),publication.reconciledAt(),publication.publishedAt(),true,List.copyOf(periods));
    }
    private Period period(LocalDate from,LocalDate to,FinancialAmounts value) {
        return new Period(from,to,value.cashReceived(),value.depositLiability(),value.earnedRevenue(),value.refunds(),value.outstandingReceivable());
    }
}
