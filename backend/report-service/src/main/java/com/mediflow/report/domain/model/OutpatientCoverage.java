package com.mediflow.report.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Accepted finite source coverage; local event silence or latest-event time never creates it. */
public record OutpatientCoverage(UUID coverageId,LocalDate from,LocalDate to,UUID departmentId,
        String zoneId,String currency,Map<String,String> sourceReferences,Instant sourceObservedAt,
        Instant reconciledAt,Instant acceptedAt,UUID acceptedBy,String acceptanceReference,String evidenceFingerprint) {
    public static final Set<String> REQUIRED_SOURCES=Set.of("CLINICAL","LAB","PHARMACY","BILLING_CASH","BILLING_REFUND");
    public OutpatientCoverage {
        if(coverageId==null||from==null||to==null||to.isBefore(from)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>=366
                ||zoneId==null||!"VND".equals(currency)
                ||sourceReferences==null||!sourceReferences.keySet().equals(REQUIRED_SOURCES)
                ||sourceReferences.values().stream().anyMatch(v->v==null||v.isBlank()||v.length()>255)
                ||sourceObservedAt==null||reconciledAt==null||acceptedAt==null||acceptedBy==null
                ||reconciledAt.isBefore(sourceObservedAt)||acceptedAt.isBefore(reconciledAt)
                ||acceptanceReference==null||acceptanceReference.isBlank()||acceptanceReference.length()>255
                ||evidenceFingerprint==null||!evidenceFingerprint.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Complete accepted outpatient coverage is required");
        ZoneId zone=ZoneId.of(zoneId);
        if(sourceObservedAt.isBefore(to.plusDays(1).atStartOfDay(zone).toInstant()))
            throw new IllegalArgumentException("Source acceptance must cover the complete final business day");
        sourceReferences=Map.copyOf(sourceReferences);
    }
    public boolean covers(LocalDate start,LocalDate end,UUID department,String zone,String money,Instant now) {
        return !start.isBefore(from)&&!end.isAfter(to)&&java.util.Objects.equals(departmentId,department)
                &&zoneId.equals(zone)&&currency.equals(money)&&!acceptedAt.isAfter(now);
    }
}
