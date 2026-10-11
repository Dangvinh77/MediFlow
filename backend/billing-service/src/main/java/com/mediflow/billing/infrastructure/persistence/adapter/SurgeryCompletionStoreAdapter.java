package com.mediflow.billing.infrastructure.persistence.adapter;

import com.mediflow.billing.application.dto.command.CaptureSurgeryCompletionCommand;
import com.mediflow.billing.application.port.out.SurgeryCompletionStorePort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@Transactional(propagation=Propagation.MANDATORY)
public class SurgeryCompletionStoreAdapter implements SurgeryCompletionStorePort {
    private final JdbcTemplate jdbc;
    public SurgeryCompletionStoreAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Override public boolean lockAndFindReplay(CaptureSurgeryCompletionCommand command) {
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Completion proof requires an active transaction");
        jdbc.execute("SET LOCAL lock_timeout='3s'");
        var event=command.event();
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","surgery-charge:"+event.surgeryCaseId());
        var planned=jdbc.queryForList("""
                SELECT s.surgery_request_id,s.record_id,p.account_id FROM surgery_charge_source s
                JOIN payment_request p ON p.payment_request_id=s.payment_request_id WHERE s.surgery_case_id=?
                """,event.surgeryCaseId());
        require(!planned.isEmpty()); var plan=planned.getFirst();
        same(command.surgeryRequestId(),plan.get("surgery_request_id")); same(event.payload().recordId(),plan.get("record_id"));
        var account=jdbc.queryForMap("SELECT * FROM billing_account WHERE account_id=? FOR UPDATE",plan.get("account_id"));
        same(event.patientId(),account.get("patient_id")); same(command.careEpisodeType(),account.get("care_episode_type"));
        same(command.careEpisodeId(),account.get("care_episode_id"));
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))","billing-surgery-completion-delivery:"+event.eventId());
        require(!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM surgery_charge_delivery WHERE event_id=?
                    UNION ALL SELECT 1 FROM admission_charge_close_delivery WHERE event_id=?
                    UNION ALL SELECT 1 FROM prescription_charge_delivery WHERE event_id=?
                    UNION ALL SELECT 1 FROM prescription_terminal_delivery WHERE event_id=?)
                """,Boolean.class,event.eventId(),event.eventId(),event.eventId(),event.eventId())));
        var previous=jdbc.queryForList("SELECT * FROM surgery_completion_source WHERE surgery_case_id=?",event.surgeryCaseId());
        if(!previous.isEmpty()) {
            var proof=previous.getFirst(); same(command.sourceFingerprint(),proof.get("source_fingerprint"));
            same(command.surgeryRequestId(),proof.get("surgery_request_id")); same(plan.get("account_id"),proof.get("account_id"));
            same(event.patientId(),proof.get("patient_id")); same(event.departmentId(),proof.get("department_id"));
            same(command.careEpisodeType(),proof.get("care_episode_type")); same(command.careEpisodeId(),proof.get("care_episode_id"));
            same(event.resultId(),proof.get("result_id")); same(command.caseRevision(),proof.get("case_revision"));
            same(command.sourceRevision(),proof.get("source_revision")); same(command.completedAtIso(),proof.get("completed_at_iso"));
            same(command.recordedAtIso(),proof.get("recorded_at_iso"));
            validateReconciledLines(command,(UUID)plan.get("account_id")); saveDelivery(command); return true;
        }
        // A compatibility event has no canonical source proof: it cannot be upgraded by guessing.
        require(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM processed_event WHERE event_id=?)",Boolean.class,event.eventId())));
        require("OPEN".equals(account.get("status")));
        var alreadyPerformed=jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM charge WHERE source_type='SURGERY' AND source_id=? AND reconciled_result_id IS NOT NULL)
                """,Boolean.class,event.surgeryCaseId());
        require(!Boolean.TRUE.equals(alreadyPerformed));
        var delivery=jdbc.queryForList("SELECT event_id FROM surgery_completion_delivery WHERE event_id=?",event.eventId());
        require(delivery.isEmpty()); return false;
    }
    @Override public void save(CaptureSurgeryCompletionCommand command) {
        var event=command.event(); var account=jdbc.queryForObject("""
                SELECT p.account_id FROM surgery_charge_source s JOIN payment_request p ON p.payment_request_id=s.payment_request_id
                WHERE s.surgery_case_id=?
                """,UUID.class,event.surgeryCaseId());
        validateReconciledLines(command,account);
        jdbc.update("""
                INSERT INTO surgery_completion_source(surgery_case_id,surgery_request_id,account_id,patient_id,department_id,
                    care_episode_type,care_episode_id,result_id,case_revision,source_revision,completed_at_iso,recorded_at_iso,source_fingerprint)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,event.surgeryCaseId(),command.surgeryRequestId(),account,event.patientId(),event.departmentId(),command.careEpisodeType(),
                command.careEpisodeId(),event.resultId(),command.caseRevision(),command.sourceRevision(),command.completedAtIso(),
                command.recordedAtIso(),command.sourceFingerprint());
        saveDelivery(command);
        jdbc.update("UPDATE billing_account SET version=version+1 WHERE account_id=?",account);
    }
    private void validateReconciledLines(CaptureSurgeryCompletionCommand command,UUID account) {
        var event=command.event(); var performed=new TreeMap<String,BigDecimal>();
        event.performedItems().forEach(item->performed.merge(item.priceCode(),item.quantity(),BigDecimal::add));
        var rows=jdbc.queryForList("SELECT * FROM charge WHERE source_type='SURGERY' AND source_id=? ORDER BY charge_id LIMIT 1001",event.surgeryCaseId());
        require(!rows.isEmpty() && rows.size()<=1000 && rows.size()==performed.size());
        for(var row:rows) {
            same(account,row.get("account_id")); same(event.patientId(),row.get("patient_id")); same(event.departmentId(),row.get("department_id"));
            same("POSTED",row.get("status")); same(event.resultId(),row.get("reconciled_result_id"));
            var quantity=performed.get(row.get("price_code")); require(quantity!=null && quantity.compareTo((BigDecimal)row.get("quantity"))==0);
            require(((BigDecimal)row.get("gross_amount")).compareTo(quantity.multiply((BigDecimal)row.get("unit_amount")).setScale(2,java.math.RoundingMode.HALF_UP))==0);
        }
    }
    private void saveDelivery(CaptureSurgeryCompletionCommand command) {
        jdbc.update("INSERT INTO surgery_completion_delivery(event_id,surgery_case_id,delivery_fingerprint) VALUES (?,?,?) ON CONFLICT(event_id) DO NOTHING",
                command.event().eventId(),command.event().surgeryCaseId(),command.deliveryFingerprint());
        var row=jdbc.queryForMap("SELECT surgery_case_id,delivery_fingerprint FROM surgery_completion_delivery WHERE event_id=?",command.event().eventId());
        same(command.event().surgeryCaseId(),row.get("surgery_case_id")); same(command.deliveryFingerprint(),row.get("delivery_fingerprint"));
    }
    private static void same(Object expected,Object actual){require(Objects.equals(expected,actual));}
    private static void require(boolean valid){if(!valid)throw new BillingRuleException("BILLING_SURGERY_COMPLETION_CONFLICT","Exact performed Surgery proof required");}
}
