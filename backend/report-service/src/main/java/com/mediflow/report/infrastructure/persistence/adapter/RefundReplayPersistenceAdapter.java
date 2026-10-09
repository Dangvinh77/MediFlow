package com.mediflow.report.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.RefundReplayInput;
import com.mediflow.report.application.dto.command.carefinance.RefundReplayInput.State;
import com.mediflow.report.application.dto.response.CashReplayProgress.Status;
import com.mediflow.report.application.dto.response.RefundReplayProgress;
import com.mediflow.report.application.mapper.CashRefundProjectionPlanner;
import com.mediflow.report.application.port.out.RefundReplayStorePort;
import com.mediflow.report.domain.model.CashReceipt;
import com.mediflow.report.domain.model.CashRefund;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class RefundReplayPersistenceAdapter implements RefundReplayStorePort {
    private static final Set<String> FIELDS = Set.of("refundTransactionId", "originalTransactionId", "accountId",
            "patientId", "departmentId", "careEpisodeType", "careEpisodeId", "amount", "currency", "completedAt",
            "businessDate", "reportZone", "state", "classification", "originalBusinessDate", "reasonCode");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CashSnapshotCodec receipts;
    public RefundReplayPersistenceAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc; this.mapper = mapper.copy(); this.receipts = new CashSnapshotCodec(mapper);
    }
    @Override public RefundReplayProgress freeze(UUID id) {
        // Reject callers joining a lower-isolation outer transaction instead of silently mixing snapshots.
        require("repeatable read".equals(jdbc.queryForObject("SHOW transaction_isolation", String.class)), "Paired replay needs repeatable read");
        jdbc.update("INSERT INTO refund_replay_generation(generation_id,status) VALUES (?,'BUILDING')", id);
        jdbc.update("""
                INSERT INTO refund_replay_input(generation_id,refund_transaction_id,original_transaction_id,
                    first_event_id,state,fact_snapshot,fact_fingerprint,source_fingerprint,first_envelope_fingerprint)
                SELECT ?,r.refund_transaction_id,r.original_transaction_id,r.first_event_id,r.state,s.snapshot,
                    encode(sha256(convert_to(s.snapshot::text,'UTF8')),'hex'),r.source_fingerprint,d.envelope_fingerprint
                FROM report_cash_refund r
                LEFT JOIN report_cash_refund_delivery d ON d.event_id=r.first_event_id AND d.refund_transaction_id=r.refund_transaction_id
                CROSS JOIN LATERAL (SELECT jsonb_build_object(
                    'refundTransactionId',r.refund_transaction_id,'originalTransactionId',r.original_transaction_id,
                    'accountId',r.account_id,'patientId',r.patient_id,'departmentId',r.department_id,
                    'careEpisodeType',r.care_episode_type,'careEpisodeId',r.care_episode_id,'amount',r.amount,
                    'currency',r.currency,'completedAt',r.completed_at_iso,'businessDate',r.business_date,
                    'reportZone',r.report_zone,'state',r.state,'classification',r.classification,
                    'originalBusinessDate',r.original_business_date,'reasonCode',r.reason_code) AS snapshot) s
                """, id);
        jdbc.update("""
                UPDATE refund_replay_generation SET source_refunds=(SELECT count(*) FROM refund_replay_input WHERE generation_id=?),
                    pending_refunds=(SELECT count(*) FROM refund_replay_input WHERE generation_id=? AND state='PENDING'),
                    rejected_refunds=(SELECT count(*) FROM refund_replay_input WHERE generation_id=? AND state='REJECTED') WHERE generation_id=?
                """, id,id,id,id);
        return progress(id);
    }
    @Override public RefundReplayProgress lock(UUID id) {
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        require(jdbc.queryForList("SELECT generation_id FROM refund_replay_generation WHERE generation_id=? FOR UPDATE", UUID.class,id).size()==1,
                "Refund generation absent");
        return progress(id);
    }
    @Override public RefundReplayProgress progress(UUID id) {
        return jdbc.queryForObject("SELECT * FROM refund_replay_generation WHERE generation_id=?", (rs,row) -> new RefundReplayProgress(id,
                Status.valueOf(rs.getString("status")),rs.getLong("source_refunds"),rs.getLong("processed_refunds"),
                rs.getLong("pending_refunds"),rs.getLong("rejected_refunds"),rs.getInt("format_version")),id);
    }
    @Override public List<RefundReplayInput> pending(UUID id, int limit) {
        require(limit>=1 && limit<=500,"Invalid refund replay batch");
        return jdbc.query("""
                SELECT *,fact_fingerprint=encode(sha256(convert_to(fact_snapshot::text,'UTF8')),'hex') AS valid_hash
                FROM refund_replay_input WHERE generation_id=? AND NOT processed ORDER BY refund_transaction_id LIMIT ?
                """, (rs,row) -> {
                    require(rs.getBoolean("valid_hash"),"Corrupt frozen refund hash");
                    var input=decode(rs.getString("fact_snapshot"));
                    require(input.refund().refundTransactionId().equals(rs.getObject("refund_transaction_id",UUID.class))
                            && input.refund().originalTransactionId().equals(rs.getObject("original_transaction_id",UUID.class))
                            && input.state().name().equals(rs.getString("state")),"Frozen refund identity conflict");
                    return input;
                }, id,limit);
    }
    @Override public CashReceipt original(UUID id, UUID original) {
        return jdbc.queryForObject("SELECT * FROM cash_replay_receipt WHERE generation_id=? AND transaction_id=?", (rs,row) ->
                receipts.decode(original,rs.getString("fact_snapshot"),rs.getString("fact_fingerprint"),1,1),id,original);
    }
    @Override public BigDecimal acceptedRefundTotal(UUID id, UUID original) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM((fact_snapshot->>'amount')::numeric),0) FROM refund_replay_input
                WHERE generation_id=? AND original_transaction_id=? AND state='APPLIED'
                """,BigDecimal.class,id,original);
    }
    @Override public void restore(UUID id, RefundReplayInput input) {
        int inserted=jdbc.update("""
                INSERT INTO refund_replay_fact(generation_id,refund_transaction_id,original_transaction_id,first_event_id,state,
                    fact_snapshot,fact_fingerprint,source_fingerprint,first_envelope_fingerprint)
                SELECT generation_id,refund_transaction_id,original_transaction_id,first_event_id,state,
                    fact_snapshot,fact_fingerprint,source_fingerprint,first_envelope_fingerprint
                FROM refund_replay_input WHERE generation_id=? AND refund_transaction_id=? AND NOT processed
                ON CONFLICT (generation_id,refund_transaction_id) DO NOTHING
                """,id,input.refund().refundTransactionId());
        require(inserted==1,"Refund replay fact already exists or input exhausted");
        if (input.state()==State.APPLIED) {
            for (var scope:CashRefundProjectionPlanner.scopes(input.refund())) {
                var refund=input.refund();
                jdbc.update("""
                        INSERT INTO refund_replay_scope(generation_id,business_date,currency,report_zone,department_id,
                            classification,completed_refunds,refund_count) VALUES (?,?,?,?,?,?,?,1)
                        ON CONFLICT (generation_id,business_date,currency,report_zone,department_id,classification)
                        DO UPDATE SET completed_refunds=refund_replay_scope.completed_refunds+EXCLUDED.completed_refunds,
                            refund_count=refund_replay_scope.refund_count+1
                        """,id,Date.valueOf(refund.businessDate()),refund.currency(),refund.reportZone(),scope.departmentId(),
                        input.classification().name(),refund.amount());
            }
        }
        require(jdbc.update("UPDATE refund_replay_input SET processed=true WHERE generation_id=? AND refund_transaction_id=? AND NOT processed",
                id,input.refund().refundTransactionId())==1,"Refund replay progress conflict");
        jdbc.update("UPDATE refund_replay_generation SET processed_refunds=processed_refunds+1 WHERE generation_id=?",id);
    }
    @Override public RefundReplayProgress reconcile(UUID id) {
        var current=lock(id);
        if (current.status()!=Status.BUILDING) return current;
        boolean receiptsVerified=Boolean.TRUE.equals(jdbc.queryForObject("SELECT status='VERIFIED' FROM cash_replay_generation WHERE generation_id=?",Boolean.class,id));
        boolean facts=Boolean.TRUE.equals(jdbc.queryForObject("""
                WITH expected AS (SELECT refund_transaction_id,original_transaction_id,first_event_id,state,fact_snapshot,
                        fact_fingerprint,source_fingerprint,first_envelope_fingerprint FROM refund_replay_input WHERE generation_id=?),
                    actual AS (SELECT refund_transaction_id,original_transaction_id,first_event_id,state,fact_snapshot,
                        fact_fingerprint,source_fingerprint,first_envelope_fingerprint FROM refund_replay_fact WHERE generation_id=?)
                SELECT NOT EXISTS((SELECT * FROM expected EXCEPT SELECT * FROM actual) UNION ALL
                    (SELECT * FROM actual EXCEPT SELECT * FROM expected))
                """,Boolean.class,id,id));
        boolean scopes=Boolean.TRUE.equals(jdbc.queryForObject("""
                WITH inputs AS (SELECT fact_snapshot FROM refund_replay_input WHERE generation_id=? AND state='APPLIED'),
                    expanded AS (SELECT (fact_snapshot->>'businessDate')::date AS business_date,fact_snapshot->>'currency' AS currency,
                        fact_snapshot->>'reportZone' AS report_zone,(fact_snapshot->>'departmentId')::uuid AS department_id,
                        fact_snapshot->>'classification' AS classification,(fact_snapshot->>'amount')::numeric AS amount FROM inputs
                        UNION ALL SELECT (fact_snapshot->>'businessDate')::date,fact_snapshot->>'currency',fact_snapshot->>'reportZone',
                            NULL::uuid,fact_snapshot->>'classification',(fact_snapshot->>'amount')::numeric FROM inputs),
                    expected AS (SELECT business_date,currency,report_zone,department_id,classification,SUM(amount),COUNT(*) FROM expanded
                        GROUP BY business_date,currency,report_zone,department_id,classification),
                    actual AS (SELECT business_date,currency,report_zone,department_id,classification,completed_refunds,refund_count
                        FROM refund_replay_scope WHERE generation_id=?)
                SELECT NOT EXISTS((SELECT * FROM expected EXCEPT SELECT * FROM actual) UNION ALL
                    (SELECT * FROM actual EXCEPT SELECT * FROM expected))
                """,Boolean.class,id,id));
        boolean progress=Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT g.source_refunds=(SELECT count(*) FROM refund_replay_input WHERE generation_id=g.generation_id)
                    AND g.processed_refunds=g.source_refunds
                    AND g.pending_refunds=(SELECT count(*) FROM refund_replay_input WHERE generation_id=g.generation_id AND state='PENDING')
                    AND g.rejected_refunds=(SELECT count(*) FROM refund_replay_input WHERE generation_id=g.generation_id AND state='REJECTED')
                    AND g.format_version=1
                    AND NOT EXISTS(SELECT 1 FROM refund_replay_input WHERE generation_id=g.generation_id AND
                        (NOT processed OR fact_fingerprint<>encode(sha256(convert_to(fact_snapshot::text,'UTF8')),'hex')))
                FROM refund_replay_generation g WHERE generation_id=?
                """,Boolean.class,id));
        jdbc.update("UPDATE refund_replay_generation SET status=?,completed_at=now() WHERE generation_id=?",
                receiptsVerified && facts && scopes && progress ? "VERIFIED":"FAILED",id);
        return progress(id);
    }
    private RefundReplayInput decode(String snapshot) {
        try {
            JsonNode n=mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(snapshot);
            var names=new java.util.HashSet<String>(); n.fieldNames().forEachRemaining(names::add);
            require(n.isObject() && FIELDS.equals(names) && n.get("amount").isNumber(),"Invalid refund snapshot fields");
            var refund=new CashRefund(uuid(n,"refundTransactionId"),uuid(n,"originalTransactionId"),uuid(n,"accountId"),uuid(n,"patientId"),
                    uuid(n,"departmentId"),text(n,"careEpisodeType"),uuid(n,"careEpisodeId"),n.get("amount").decimalValue(),text(n,"currency"),
                    Instant.parse(text(n,"completedAt")),LocalDate.parse(text(n,"businessDate")),text(n,"reportZone"));
            return new RefundReplayInput(refund,State.valueOf(text(n,"state")),n.get("classification").isNull()?null:CashReceipt.Classification.valueOf(text(n,"classification")),
                    n.get("originalBusinessDate").isNull()?null:LocalDate.parse(text(n,"originalBusinessDate")),n.get("reasonCode").isNull()?null:text(n,"reasonCode"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalArgumentException("Invalid frozen refund JSON",invalid); }
    }
    private static String text(JsonNode n,String field) {
        require(n.get(field).isTextual() && !n.get(field).textValue().isBlank(),"Invalid frozen refund text"); return n.get(field).textValue();
    }
    private static UUID uuid(JsonNode n,String field) {
        String value=text(n,field); UUID id=UUID.fromString(value); require(id.toString().equals(value),"Invalid frozen refund UUID"); return id;
    }
    private static void require(boolean valid,String message) { if (!valid) throw new IllegalArgumentException(message); }
}
