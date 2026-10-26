package com.mediflow.billing.infrastructure.persistence.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.port.out.OutpatientQuoteRepositoryPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.domain.model.OutpatientQuote;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(name = {"mediflow.billing.ledger.enabled", "mediflow.billing.outpatient-quotes.enabled"}, havingValue = "true")
public class OutpatientQuoteRepositoryAdapter implements OutpatientQuoteRepositoryPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public OutpatientQuoteRepositoryAdapter(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    @Override @Transactional(propagation = Propagation.MANDATORY, timeout = 5)
    public void append(UUID paymentRequestId, OutpatientQuote quote) {
        var rows = jdbc.queryForList("""
                SELECT p.status,p.requested_amount,p.expires_at,a.patient_id,a.care_episode_type,a.care_episode_id
                  FROM payment_request p JOIN billing_account a ON a.account_id=p.account_id
                 WHERE p.payment_request_id=? FOR UPDATE OF p
                """, paymentRequestId);
        if (rows.size() != 1) fail();
        var row = rows.getFirst();
        if (!"PENDING".equals(row.get("status")) || !quote.patientId().equals(row.get("patient_id"))
                || !quote.careEpisodeType().equals(row.get("care_episode_type")) || !quote.careEpisodeId().equals(row.get("care_episode_id"))
                || quote.patientPayable().compareTo((BigDecimal)row.get("requested_amount")) != 0
                || row.get("expires_at") == null || java.time.Duration.between(quote.expiresAt(), ((Timestamp)row.get("expires_at")).toInstant()).abs().compareTo(java.time.Duration.ofNanos(1000)) >= 0) fail();
        Integer paid = jdbc.queryForObject("SELECT count(*) FROM payment_transaction WHERE payment_request_id=? AND status='COMPLETED'", Integer.class, paymentRequestId);
        Integer pending = jdbc.queryForObject("SELECT count(*) FROM scheduled_payment_operation WHERE payment_request_id=? AND status IN ('PREPARING','COMPLETED')", Integer.class, paymentRequestId);
        if (paid == null || pending == null || paid != 0 || pending != 0) fail();
        Integer linked = jdbc.queryForObject("SELECT count(*) FROM payment_request_charge WHERE payment_request_id=?", Integer.class, paymentRequestId);
        if (linked == null || linked != quote.lines().size()) fail();
        for (var line : quote.lines()) {
            Integer matched = jdbc.queryForObject("""
                    SELECT count(*) FROM payment_request_charge r JOIN charge c ON c.charge_id=r.charge_id
                     WHERE r.payment_request_id=? AND c.source_id=? AND c.source_type=? AND c.price_code=?
                       AND c.patient_id=? AND c.quantity=? AND c.unit_amount=? AND c.gross_amount=?
                       AND r.requested_amount=? AND c.status='POSTED'
                    """, Integer.class, paymentRequestId, line.sourceId(), line.sourceType(), line.priceCode(), quote.patientId(),
                    line.quantity(), line.unitPrice(), line.grossAmount(), line.patientPayable());
            if (matched == null || matched != 1) fail();
        }
        String snapshot;
        try { snapshot = mapper.writeValueAsString(quote); }
        catch (Exception invalid) { throw new IllegalArgumentException("Cannot serialize quote snapshot", invalid); }
        jdbc.update("""
                INSERT INTO outpatient_quote(quote_id,payment_request_id,patient_id,care_episode_id,issued_at,expires_at,
                    gross_amount,support_amount,patient_payable,snapshot) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb)
                """, quote.quoteId(), paymentRequestId, quote.patientId(), quote.careEpisodeId(), Timestamp.from(quote.issuedAt()),
                Timestamp.from(quote.expiresAt()), quote.grossAmount(), quote.supportAmount(), quote.patientPayable(), snapshot);
        jdbc.update("""
                INSERT INTO outpatient_current_quote(payment_request_id,quote_id) VALUES (?,?)
                ON CONFLICT(payment_request_id) DO UPDATE SET quote_id=EXCLUDED.quote_id
                """, paymentRequestId, quote.quoteId());
    }

    @Override @Transactional(readOnly = true, timeout = 3)
    public Optional<OutpatientQuote> findCurrent(UUID paymentRequestId) {
        return jdbc.query("""
                SELECT q.snapshot FROM outpatient_current_quote c JOIN outpatient_quote q ON q.quote_id=c.quote_id
                 WHERE c.payment_request_id=?
                """, (rs, index) -> {
            try { return mapper.readValue(rs.getString(1), OutpatientQuote.class); }
            catch (Exception invalid) { throw new org.springframework.dao.DataIntegrityViolationException("Invalid stored outpatient quote", invalid); }
        }, paymentRequestId).stream().findFirst();
    }
    private static void fail() { throw new BillingRuleException("BILLING_QUOTE_BINDING_CONFLICT", "Quote does not match its pending request and source charges"); }
}
