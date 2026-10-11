package com.mediflow.billing.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.dto.command.FinalizeAdmissionCommand;
import com.mediflow.billing.application.event.LedgerIntegrationEvent.FinalSettlementRecognitionPayload;
import com.mediflow.billing.application.port.out.FinalAdmissionSettlementStorePort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.AdmissionFinalizationPlan;
import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.common.exception.ResourceNotFoundException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class FinalAdmissionSettlementStoreAdapter implements FinalAdmissionSettlementStorePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public FinalAdmissionSettlementStoreAdapter(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    @Override public Optional<FinalSettlementRecognitionPayload> lockAndFindReplay(FinalizeAdmissionCommand command) {
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Final settlement requires an active transaction");
        jdbc.execute("SET LOCAL lock_timeout='3s'");jdbc.execute("SET LOCAL statement_timeout='3s'");
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","final-admission-settlement-key:"+command.idempotencyKey());
        var rows=jdbc.queryForList("SELECT * FROM final_admission_settlement_operation WHERE idempotency_key=?",command.idempotencyKey());
        if(rows.isEmpty())return Optional.empty();var row=rows.getFirst();
        if(!command.accountId().equals(row.get("account_id")) || !command.actorId().equals(row.get("actor_id")))
            throw new DuplicateResourceException("BILLING_FINAL_SETTLEMENT_KEY_CONFLICT","Key belongs to another finalization intent");
        try{return Optional.of(mapper.readValue(row.get("payload").toString(),FinalSettlementRecognitionPayload.class));}
        catch(com.fasterxml.jackson.core.JsonProcessingException failure){throw new IllegalStateException("Cannot read immutable final settlement");}
    }
    @Override public Evidence lockAndReadEvidence(UUID accountId) {
        var rows=jdbc.queryForList("SELECT * FROM billing_account WHERE account_id=? FOR UPDATE",accountId);
        if(rows.isEmpty())throw new ResourceNotFoundException("BILLING_ACCOUNT_NOT_FOUND","Billing account not found");
        var account=rows.getFirst();
        require("ADMISSION".equals(account.get("care_episode_type")) && java.util.Set.of("CHARGE_CLOSED","SETTLEMENT_PENDING").contains(account.get("status")));
        require(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM settlement WHERE account_id=?)",Boolean.class,accountId)));
        var medical=jdbc.queryForList("SELECT * FROM admission_charge_close WHERE account_id=? AND status='APPLIED'",accountId);
        require(medical.size()==1);var approval=medical.getFirst();
        same(account.get("patient_id"),approval.get("patient_id"));same(account.get("care_episode_id"),approval.get("admission_id"));
        jdbc.update("UPDATE billing_account SET version=version+1 WHERE account_id=?",accountId);
        var proofs=new ArrayList<AdmissionFinalizationPlan.PerformedCharge>();
        var charges=jdbc.queryForList("SELECT * FROM charge WHERE account_id=? AND status='POSTED' ORDER BY charge_id LIMIT 501",accountId);
        require(charges.size()<=500);
        for(var charge:charges) {
            same(account.get("patient_id"),charge.get("patient_id"));UUID sourceId=(UUID)charge.get("source_id");
            String type=charge.get("source_type").toString(); UUID operation;String fingerprint;Instant performed;
            if("PRESCRIPTION".equals(type)) {
                var rx=jdbc.queryForList("""
                        SELECT r.*,t.event_type,t.status AS terminal_status,t.source_fingerprint AS terminal_fingerprint,
                            t.snapshot_fingerprint AS terminal_snapshot,t.terminal_at_iso
                        FROM prescription_charge_source r JOIN prescription_terminal_source t USING(prescription_id)
                        WHERE r.prescription_id=?
                        """,sourceId);
                require(rx.size()==1);var row=rx.getFirst();same(accountId,row.get("account_id"));same(charge.get("charge_id"),row.get("charge_id"));
                same("ADMISSION",row.get("care_context"));same("APPLIED",row.get("terminal_status"));same("prescription.filled",row.get("event_type"));
                require(row.get("snapshot_fingerprint")!=null);same(row.get("snapshot_fingerprint"),row.get("terminal_snapshot"));
                operation=sourceId;fingerprint=row.get("terminal_fingerprint").toString();performed=Instant.parse(row.get("terminal_at_iso").toString());
            }else if("SURGERY".equals(type)) {
                var surgery=jdbc.queryForList("SELECT * FROM surgery_completion_source WHERE surgery_case_id=?",sourceId);
                require(surgery.size()==1);var row=surgery.getFirst();same(accountId,row.get("account_id"));same(account.get("patient_id"),row.get("patient_id"));
                same(charge.get("department_id"),row.get("department_id"));same("ADMISSION",row.get("care_episode_type"));
                same(account.get("care_episode_id"),row.get("care_episode_id"));same(charge.get("reconciled_result_id"),row.get("result_id"));
                operation=(UUID)row.get("result_id");fingerprint=row.get("source_fingerprint").toString();performed=Instant.parse(row.get("completed_at_iso").toString());
            }else throw rule();
            proofs.add(new AdmissionFinalizationPlan.PerformedCharge((UUID)charge.get("charge_id"),(UUID)charge.get("department_id"),
                    type,sourceId,operation,fingerprint,performed));
        }
        var allocations=jdbc.query("""
                SELECT a.*,t.original_transaction_id,t.transaction_type,t.classification FROM payment_allocation a
                JOIN payment_transaction t ON t.transaction_id=a.transaction_id JOIN charge c ON c.charge_id=a.charge_id
                WHERE t.account_id=? OR c.account_id=? ORDER BY a.allocation_id LIMIT 1001
                """,(rs,row)->new FinalSettlementRecognitionPayload.AllocationEvidence(rs.getObject("allocation_id",UUID.class),
                rs.getObject("transaction_id",UUID.class),rs.getObject("original_transaction_id",UUID.class),rs.getObject("charge_id",UUID.class),
                rs.getString("transaction_type"),rs.getString("classification"),rs.getBigDecimal("amount")),accountId,accountId);
        require(allocations.size()<=1000);return new Evidence(Instant.parse(approval.get("approved_at_iso").toString()),proofs,allocations);
    }
    @Override public void save(FinalizeAdmissionCommand command,FinalSettlementRecognitionPayload payload) {
        jdbc.update("""
                INSERT INTO settlement(settlement_id,account_id,admission_id,settlement_version,supersedes_settlement_id,
                    gross_amount,insurance_amount,patient_liability,completed_payments,completed_refunds,balance,outcome,completed_at)
                VALUES (?,?,?,1,NULL,?,?,?,?,?,?,'PAID_IN_FULL',?)
                """,payload.settlementId(),payload.accountId(),payload.admissionId(),payload.grossAmount(),payload.insuranceAmount(),payload.patientLiability(),
                payload.completedPayments(),payload.completedRefunds(),payload.balance(),Timestamp.from(payload.completedAt()));
        try{jdbc.update("""
                INSERT INTO final_admission_settlement_operation(recognition_operation_id,settlement_id,account_id,actor_id,idempotency_key,completed_at,payload)
                VALUES (?,?,?,?,?,?,?)
                """,payload.recognitionOperationId(),payload.settlementId(),command.accountId(),command.actorId(),command.idempotencyKey(),
                Timestamp.from(payload.completedAt()),mapper.writeValueAsString(payload));}
        catch(com.fasterxml.jackson.core.JsonProcessingException failure){throw new IllegalStateException("Cannot encode immutable final settlement");}
        require(jdbc.update("UPDATE billing_account SET status='SETTLED',version=version+1,updated_at=? WHERE account_id=? AND status IN ('CHARGE_CLOSED','SETTLEMENT_PENDING')",
                Timestamp.from(payload.completedAt()),command.accountId())==1);
    }
    private static void same(Object expected,Object actual){require(Objects.equals(expected,actual));}
    private static void require(boolean valid){if(!valid)throw rule();}
    private static BillingRuleException rule(){return new BillingRuleException("BILLING_FINAL_SETTLEMENT_EVIDENCE_UNAVAILABLE","Exact final settlement evidence is unavailable");}
}
