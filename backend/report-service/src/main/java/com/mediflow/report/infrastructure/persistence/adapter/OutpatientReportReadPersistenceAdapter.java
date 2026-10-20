package com.mediflow.report.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.port.out.OutpatientReportReadPort;
import com.mediflow.report.application.port.out.OutpatientCoverageEvidencePort;
import com.mediflow.report.domain.model.OutpatientCoverage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Read only Report-owned evidence. Accepted coverage is empty until audited source reconciliation. */
@Repository @Profile("mvp-outpatient | outpatient-mvp")
@ConditionalOnProperty(name={"mediflow.features.care-finance-v2","mediflow.report.outpatient-read.enabled"},havingValue="true")
@Transactional(transactionManager = "reportTransactionManager", propagation=Propagation.MANDATORY,readOnly=true)
public class OutpatientReportReadPersistenceAdapter implements OutpatientReportReadPort, OutpatientCoverageEvidencePort {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;
    public OutpatientReportReadPersistenceAdapter(@org.springframework.beans.factory.annotation.Qualifier("reportJdbcTemplate") JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    @Override
    public Optional<Evidence> load(LocalDate from,LocalDate to,UUID department,String zone,String currency){
        var proofs=jdbc.query("""
                SELECT * FROM report_outpatient_coverage
                WHERE covered_from<=? AND covered_to>=? AND department_id IS NOT DISTINCT FROM CAST(? AS UUID)
                    AND zone_id=? AND currency=? ORDER BY accepted_at DESC,coverage_id LIMIT 1
                """,(rs,n)->{
            try{return new OutpatientCoverage(rs.getObject("coverage_id",UUID.class),rs.getDate("covered_from").toLocalDate(),
                    rs.getDate("covered_to").toLocalDate(),rs.getObject("department_id",UUID.class),rs.getString("zone_id"),
                    rs.getString("currency"),mapper.readValue(rs.getString("source_references"),new TypeReference<Map<String,String>>(){ }),
                    rs.getTimestamp("source_observed_at").toInstant(),rs.getTimestamp("reconciled_at").toInstant(),
                    rs.getTimestamp("accepted_at").toInstant(),rs.getObject("accepted_by",UUID.class),
                    rs.getString("acceptance_reference"),rs.getString("local_evidence_fingerprint"));
            }catch(java.io.IOException|IllegalArgumentException|java.time.DateTimeException invalid){return null;}
        },Date.valueOf(from),Date.valueOf(to),department,zone,currency);
        if(proofs.isEmpty()||proofs.getFirst()==null)return Optional.empty();
        var proof=proofs.getFirst();
        Date start=Date.valueOf(proof.from()),end=Date.valueOf(proof.to());
        var rows=loadRows(start,end,department,zone,currency);
        boolean invalid=rows.stream().anyMatch(r->!(r.get("fingerprint") instanceof String f)||!f.matches("[0-9a-f]{64}")
                ||!Boolean.TRUE.equals(r.get("zone_valid"))||(r.containsKey("state")&&!"APPLIED".equals(r.get("state"))));
        String fingerprint=evidenceFingerprint(rows);
        var dates=new TreeMap<LocalDate,Accumulator>();
        for(var row:rows){
            LocalDate date=((Date)row.get("business_date")).toLocalDate();
            if(date.isBefore(from)||date.isAfter(to))continue;
            var a=dates.computeIfAbsent(date,d->new Accumulator());
            var amount=(BigDecimal)row.get("amount");
            switch((String)row.get("kind")){
                case "COMPLETED_VISITS"->a.visits=Math.addExact(a.visits,amount.longValueExact());
                case "LAB_TESTS"->a.lab=Math.addExact(a.lab,amount.longValueExact());
                case "DISPENSED_PRESCRIPTIONS"->a.rx=Math.addExact(a.rx,amount.longValueExact());
                case "DISPENSED_UNITS"->a.units=Math.addExact(a.units,amount.longValueExact());
                case "CASH"->a.cash=a.cash.add(amount);
                case "REFUND"->{if("APPLIED".equals(row.get("state")))a.refunds=a.refunds.add(amount);}
                default->throw new IllegalArgumentException("Unsupported outpatient read source");
            }
        }
        var days=dates.entrySet().stream().map(e->new Day(e.getKey(),e.getValue().visits,e.getValue().lab,
                e.getValue().rx,e.getValue().units,e.getValue().cash,e.getValue().refunds)).toList();
        return Optional.of(new Evidence(proof,fingerprint,invalid,days));
    }
    private List<Map<String,Object>> loadRows(Date start,Date end,UUID department,String zone,String currency) {
        var rows=new ArrayList<Map<String,Object>>();
        rows.addAll(jdbc.queryForList("""
                SELECT 'OP:'||source_type||':'||source_id||':'||source_revision||':'||metric_type AS evidence_key,
                    metric_date AS business_date,metric_type AS kind,numeric_value AS amount,fact_fingerprint AS fingerprint,
                    department_id,category,care_episode_type,care_episode_id,occurred_at,
                    (metric_date=(occurred_at AT TIME ZONE ?)::date) AS zone_valid
                FROM operational_contribution WHERE metric_date BETWEEN ? AND ?
                    AND (?::uuid IS NULL OR department_id=?::uuid)
                    AND ((source_type='MEDICAL_RECORD' AND metric_type='COMPLETED_VISITS'
                        AND category IN ('OUTPATIENT_FOLLOW_UP','PRESCRIPTION','OTHER'))
                        OR (care_episode_type='OUTPATIENT_VISIT' AND
                            ((source_type='LAB_RESULT' AND metric_type='LAB_TESTS')
                            OR (source_type='DISPENSE' AND metric_type IN ('DISPENSED_PRESCRIPTIONS','DISPENSED_UNITS')))))
                ORDER BY evidence_key
                """,zone,start,end,department,department));
        rows.addAll(jdbc.queryForList("""
                SELECT 'CASH:'||transaction_id AS evidence_key,business_date,'CASH' AS kind,amount,
                    fact_fingerprint AS fingerprint,department_id,payload_fingerprint,completed_at_iso,
                    payment_request_id,account_id,care_episode_id,
                    (business_date=(completed_at AT TIME ZONE ?)::date) AS zone_valid
                FROM report_cash_receipt WHERE business_date BETWEEN ? AND ?
                    AND (?::uuid IS NULL OR department_id=?::uuid) AND currency=? AND report_zone=?
                    AND care_episode_type='OUTPATIENT_VISIT' AND classification='SERVICE_PAYMENT' ORDER BY evidence_key
                """,zone,start,end,department,department,currency,zone));
        rows.addAll(jdbc.queryForList("""
                SELECT 'REFUND:'||refund_transaction_id AS evidence_key,business_date,'REFUND' AS kind,amount,
                    source_fingerprint AS fingerprint,department_id,original_transaction_id,completed_at_iso,state,
                    (business_date=(CAST(completed_at_iso AS timestamptz) AT TIME ZONE ?)::date) AS zone_valid
                FROM report_cash_refund WHERE business_date BETWEEN ? AND ?
                    AND (?::uuid IS NULL OR department_id=?::uuid) AND currency=? AND report_zone=?
                    AND care_episode_type='OUTPATIENT_VISIT' AND (classification='SERVICE_PAYMENT' OR state<>'APPLIED')
                ORDER BY evidence_key
                """,zone,start,end,department,department,currency,zone));
        return rows;
    }
    @Override
    public Snapshot inspect(LocalDate from,LocalDate to,UUID department,String zone,String currency) {
        var rows=loadRows(Date.valueOf(from),Date.valueOf(to),department,zone,currency);
        boolean invalid=rows.stream().anyMatch(r->!(r.get("fingerprint") instanceof String f)||!f.matches("[0-9a-f]{64}")
                ||!Boolean.TRUE.equals(r.get("zone_valid"))||(r.containsKey("state")&&!"APPLIED".equals(r.get("state"))));
        var sources=new TreeMap<String,SourceWitness>();
        for(String source:OutpatientCoverage.REQUIRED_SOURCES) {
            var selected=rows.stream().filter(r->source.equals(sourceOf(r))).toList();
            sources.put(source,new SourceWitness(selected.size(),evidenceFingerprint(selected)));
        }
        return new Snapshot(evidenceFingerprint(rows),invalid || !validAmounts(rows),sources);
    }
    private boolean validAmounts(List<Map<String,Object>> rows) {
        var totals=new HashMap<String,BigDecimal>();
        try {
            for(var row:rows) {
                if(!(row.get("amount") instanceof BigDecimal amount) || amount.signum()<0) return false;
                String kind=(String)row.get("kind");
                var total=totals.merge(kind,amount,BigDecimal::add);
                if(kind.equals("CASH") || kind.equals("REFUND")) {
                    if(total.setScale(2,java.math.RoundingMode.UNNECESSARY).precision()>19) return false;
                } else { amount.longValueExact(); total.longValueExact(); }
            }
            return true;
        } catch(ArithmeticException invalid) { return false; }
    }
    private String sourceOf(Map<String,Object> row) {
        return switch((String)row.get("kind")) {
            case "COMPLETED_VISITS" -> "CLINICAL";
            case "LAB_TESTS" -> "LAB";
            case "DISPENSED_PRESCRIPTIONS", "DISPENSED_UNITS" -> "PHARMACY";
            case "CASH" -> "BILLING_CASH";
            case "REFUND" -> "BILLING_REFUND";
            default -> throw new IllegalArgumentException("Unsupported outpatient evidence");
        };
    }
    /** Deterministic local witness; it does NOT prove external source completeness or accept coverage. */
    public static String evidenceFingerprint(List<Map<String,Object>> rows){
        try{
            var digest=MessageDigest.getInstance("SHA-256");
            for(var row:rows.stream().sorted(Comparator.comparing(r->r.get("evidence_key").toString())).toList()){
                var ordered=new TreeMap<>(row);
                for(var entry:ordered.entrySet())for(Object value:List.of(entry.getKey(),canonical(entry.getValue()))){
                    byte[] bytes=value.toString().getBytes(StandardCharsets.UTF_8);
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private static String canonical(Object value){
        if(value instanceof Timestamp time)return time.toInstant().toString();
        if(value instanceof Date date)return date.toLocalDate().toString();
        if(value instanceof BigDecimal decimal)return decimal.stripTrailingZeros().toPlainString();
        return String.valueOf(value);
    }
    private static class Accumulator{long visits,lab,rx,units;BigDecimal cash=new BigDecimal("0.00"),refunds=new BigDecimal("0.00");}
}
