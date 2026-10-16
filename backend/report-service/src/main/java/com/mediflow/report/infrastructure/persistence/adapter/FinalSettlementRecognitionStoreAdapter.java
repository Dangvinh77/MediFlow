package com.mediflow.report.infrastructure.persistence.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.mapper.BillingFinalSettlementRecognitionMapper;
import com.mediflow.report.application.port.out.FinalSettlementRecognitionStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.CashReceipt;
import com.mediflow.report.domain.model.CashRefund;
import com.mediflow.report.domain.model.FinalSettlementRecognition;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Accepted local cash evidence only. No foreign database, live lookup or automatic publication. */
@Component
@Transactional(transactionManager = "reportTransactionManager", propagation=Propagation.MANDATORY)
public class FinalSettlementRecognitionStoreAdapter implements FinalSettlementRecognitionStorePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CashSnapshotCodec codec;
    public FinalSettlementRecognitionStoreAdapter(@org.springframework.beans.factory.annotation.Qualifier("reportJdbcTemplate") JdbcTemplate jdbc,ObjectMapper mapper) {
        this.jdbc=jdbc;this.mapper=mapper.copy();this.codec=new CashSnapshotCodec(mapper);
    }
    @Override public void apply(DecodedCareFinanceEvent event,FinalSettlementRecognition fact) {
        lockAccount(fact.accountId());
        var m=event.metadata();String source=codec.evidenceFingerprint(event.payload()),projection=factHash(event.payload(),fact);
        var existing=jdbc.queryForList("SELECT recognition_operation_id FROM report_final_recognition WHERE account_id=? OR settlement_id=?",fact.accountId(),fact.settlementId());
        require(existing.stream().allMatch(row->fact.recognitionOperationId().equals(row.get("recognition_operation_id"))),"REPORT_FINAL_ACCOUNT_CONFLICT");
        jdbc.update("""
                INSERT INTO report_final_recognition(recognition_operation_id,account_id,settlement_id,first_event_id,
                    payload_snapshot,payload_fingerprint,fact_fingerprint,report_zone,business_date,completed_at_iso)
                VALUES (?,?,?,?,?::jsonb,?,?,?,?,?) ON CONFLICT(recognition_operation_id) DO NOTHING
                """,fact.recognitionOperationId(),fact.accountId(),fact.settlementId(),m.eventId(),codec.json(event.payload()),source,projection,
                fact.reportZone(),Date.valueOf(fact.businessDate()),fact.completedAt().toString());
        var stored=jdbc.queryForMap("SELECT * FROM report_final_recognition WHERE recognition_operation_id=? FOR UPDATE",fact.recognitionOperationId());
        require(source.equals(stored.get("payload_fingerprint")) && projection.equals(stored.get("fact_fingerprint")),"REPORT_FINAL_SOURCE_CONFLICT");
        var envelope=new LinkedHashMap<String,Object>();envelope.put("eventId",m.eventId().toString());envelope.put("eventType",m.eventType());
        envelope.put("version",m.version());envelope.put("occurredAt",m.occurredAt().toString());envelope.put("correlationId",m.correlationId());
        envelope.put("producer",m.producer());envelope.put("payload",event.payload());String hash=codec.evidenceFingerprint(envelope);
        jdbc.update("INSERT INTO report_final_recognition_delivery(event_id,recognition_operation_id,envelope_fingerprint) VALUES (?,?,?) ON CONFLICT(event_id) DO NOTHING",
                m.eventId(),fact.recognitionOperationId(),hash);
        var delivery=jdbc.queryForMap("SELECT * FROM report_final_recognition_delivery WHERE event_id=?",m.eventId());
        require(fact.recognitionOperationId().equals(delivery.get("recognition_operation_id")) && hash.equals(delivery.get("envelope_fingerprint")),"REPORT_FINAL_DELIVERY_CONFLICT");
        require(!"REJECTED".equals(stored.get("state")),"REPORT_FINAL_REJECTED");
        if("PENDING".equals(stored.get("state")))tryApply(fact);
    }
    @Override public void recover(UUID operationId) {
        var candidates=jdbc.queryForList("SELECT account_id FROM report_final_recognition WHERE recognition_operation_id=?",operationId);
        if(candidates.isEmpty())return;lockAccount((UUID)candidates.getFirst().get("account_id"));
        var stored=jdbc.queryForMap("SELECT * FROM report_final_recognition WHERE recognition_operation_id=? FOR UPDATE",operationId);
        if(!"PENDING".equals(stored.get("state")))return;
        try {
            Map<String,Object> payload=mapper.readerFor(new TypeReference<Map<String,Object>>(){}).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(stored.get("payload_snapshot").toString());
            var event=new DecodedCareFinanceEvent(new CareFinanceEventMetadata((UUID)stored.get("first_event_id"),"settlement.recognition.completed",1,
                    Instant.parse((String)stored.get("completed_at_iso")),"stored-final-recognition","billing-service","recognitionOperationId",operationId),payload);
            var fact=new BillingFinalSettlementRecognitionMapper(ZoneId.of((String)stored.get("report_zone"))).map(event);
            require(codec.evidenceFingerprint(payload).equals(stored.get("payload_fingerprint")) && factHash(payload,fact).equals(stored.get("fact_fingerprint")),"REPORT_FINAL_SNAPSHOT_CONFLICT");
            tryApply(fact);
        } catch(ReportRuleException | IllegalArgumentException invalid) {
            jdbc.update("UPDATE report_final_recognition SET state='REJECTED',reason_code=? WHERE recognition_operation_id=? AND state='PENDING'",
                    invalid instanceof ReportRuleException rule?rule.getCode():"REPORT_FINAL_SNAPSHOT_INVALID",operationId);
        } catch(com.fasterxml.jackson.core.JsonProcessingException invalid) {
            jdbc.update("UPDATE report_final_recognition SET state='REJECTED',reason_code='REPORT_FINAL_SNAPSHOT_INVALID' WHERE recognition_operation_id=? AND state='PENDING'",operationId);
        }
    }
    private void tryApply(FinalSettlementRecognition fact) {
        // Same original fence as cash receipt/refund. Sort to avoid multi-original lock cycles.
        var originals=new java.util.TreeSet<UUID>();fact.allocations().forEach(line->originals.add(
                "PAYMENT".equals(line.transactionType())?line.transactionId():line.originalTransactionId()));
        fact.depositReleases().forEach(line->originals.add(line.originalTransactionId()));
        for(var original:originals)jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","report-cash:"+original);
        var receipts=jdbc.query("SELECT * FROM report_cash_receipt WHERE account_id=? ORDER BY transaction_id LIMIT 1001",(rs,row)->new CashReceipt(
                rs.getObject("transaction_id",UUID.class),rs.getObject("invoice_id",UUID.class),rs.getObject("payment_request_id",UUID.class),
                rs.getObject("account_id",UUID.class),rs.getObject("patient_id",UUID.class),rs.getObject("department_id",UUID.class),rs.getString("care_episode_type"),
                rs.getObject("care_episode_id",UUID.class),CashReceipt.Classification.valueOf(rs.getString("classification")),rs.getBigDecimal("amount"),rs.getString("currency"),
                rs.getString("payment_method"),Instant.parse(rs.getString("completed_at_iso")),rs.getDate("business_date").toLocalDate(),rs.getString("report_zone")),fact.accountId());
        var refunds=jdbc.query("SELECT * FROM report_cash_refund WHERE account_id=? AND state='APPLIED' ORDER BY refund_transaction_id LIMIT 1001",(rs,row)->new CashRefund(
                rs.getObject("refund_transaction_id",UUID.class),rs.getObject("original_transaction_id",UUID.class),rs.getObject("account_id",UUID.class),
                rs.getObject("patient_id",UUID.class),rs.getObject("department_id",UUID.class),rs.getString("care_episode_type"),rs.getObject("care_episode_id",UUID.class),
                rs.getBigDecimal("amount"),rs.getString("currency"),Instant.parse(rs.getString("completed_at_iso")),rs.getDate("business_date").toLocalDate(),rs.getString("report_zone")),fact.accountId());
        require(receipts.size()<=1000 && refunds.size()<=1000,"REPORT_FINAL_CASH_LIMIT");
        if(!fact.verifyFunding(receipts,refunds))return;
        var earned=new HashMap<>(fact.earnedByDepartment());var released=new HashMap<UUID,BigDecimal>();
        for(var release:fact.depositReleases()) {
            var already=jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM report_final_deposit_release WHERE original_transaction_id=?",BigDecimal.class,release.originalTransactionId());
            require(already.signum()==0,"REPORT_FINAL_ORIGINAL_ALREADY_RELEASED");
        }
        // All business checks precede effects; storage failures roll back the whole transaction.
        require(jdbc.update("UPDATE report_final_recognition SET state='APPLIED',applied_at=now() WHERE recognition_operation_id=? AND state='PENDING'",fact.recognitionOperationId())==1,"REPORT_FINAL_STATE_CONFLICT");
        for(var release:fact.depositReleases()) {
            jdbc.update("INSERT INTO report_final_deposit_release(recognition_operation_id,original_transaction_id,amount) VALUES (?,?,?)",fact.recognitionOperationId(),release.originalTransactionId(),release.amount());
            released.merge(release.receiptDepartmentId(),release.amount(),BigDecimal::add);
        }
        var departments=new java.util.HashSet<>(earned.keySet());departments.addAll(released.keySet());
        // Every account competes for the hospital row first, then the same sorted department
        // order. HashSet iteration is not a cross-replica lock protocol.
        contribute(fact,null,fact.totals().grossAmount(),released.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add));
        for(var department:departments.stream().sorted(java.util.Comparator.comparing(UUID::toString)).toList())
            contribute(fact,department,earned.getOrDefault(department,BigDecimal.ZERO),released.getOrDefault(department,BigDecimal.ZERO));
    }
    private void contribute(FinalSettlementRecognition fact,UUID department,BigDecimal earned,BigDecimal released) {
        jdbc.update("INSERT INTO report_final_recognition_contribution(recognition_operation_id,business_date,currency,report_zone,department_id,earned_revenue,deposit_released) VALUES (?,?,?,?,?,?,?)",
                fact.recognitionOperationId(),Date.valueOf(fact.businessDate()),fact.currency(),fact.reportZone(),department,earned,released);
        jdbc.update("""
                INSERT INTO report_final_recognition_daily(business_date,currency,report_zone,department_id,earned_revenue,deposit_released) VALUES (?,?,?,?,?,?)
                ON CONFLICT(business_date,currency,report_zone,department_id) DO UPDATE SET
                    earned_revenue=report_final_recognition_daily.earned_revenue+EXCLUDED.earned_revenue,
                    deposit_released=report_final_recognition_daily.deposit_released+EXCLUDED.deposit_released
                """,Date.valueOf(fact.businessDate()),fact.currency(),fact.reportZone(),department,earned,released);
    }
    @Override @Transactional(transactionManager = "reportTransactionManager", readOnly=true,propagation=Propagation.REQUIRES_NEW,timeout=5) public List<UUID> pending(int limit) {
        require(limit>=1 && limit<=20,"REPORT_FINAL_BATCH_INVALID");
        return jdbc.queryForList("SELECT recognition_operation_id FROM report_final_recognition WHERE state='PENDING' AND next_attempt_at<=now() ORDER BY created_at,recognition_operation_id LIMIT ?",UUID.class,limit);
    }
    @Override @Transactional(transactionManager = "reportTransactionManager", propagation=Propagation.REQUIRES_NEW,timeout=5) public void defer(UUID operationId) {
        jdbc.update("UPDATE report_final_recognition SET next_attempt_at=now()+interval '60 seconds',recovery_attempts=recovery_attempts+1 WHERE recognition_operation_id=? AND state='PENDING'",operationId);
    }
    private void lockAccount(UUID accountId) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");jdbc.execute("SET LOCAL statement_timeout='5s'");
        FinancialPublicationFence.writer(jdbc);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","report-final-account:"+accountId);
    }
    private String factHash(Map<String,Object> payload,FinalSettlementRecognition fact) {
        return codec.evidenceFingerprint(Map.of("payload",payload,"reportZone",fact.reportZone(),"businessDate",fact.businessDate().toString()));
    }
    private static void require(boolean valid,String code){if(!valid)throw new ReportRuleException(code,"Final recognition evidence conflict");}
}
