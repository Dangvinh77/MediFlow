package com.mediflow.report.application.port.out;

import com.mediflow.report.domain.model.OutpatientCoverage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutpatientReportReadPort {
    Optional<Evidence> load(LocalDate from,LocalDate to,UUID departmentId,String zone,String currency);
    record Day(LocalDate date,long completedVisits,long labTests,long dispensedPrescriptions,long dispensedUnits,
            BigDecimal cashReceived,BigDecimal refunds){ }
    record Evidence(OutpatientCoverage coverage,String currentFingerprint,boolean pendingOrInvalid,List<Day> days){
        public Evidence {days=List.copyOf(days);}
    }
}
