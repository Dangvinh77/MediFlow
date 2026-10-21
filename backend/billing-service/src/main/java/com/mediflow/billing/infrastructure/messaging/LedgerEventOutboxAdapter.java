package com.mediflow.billing.infrastructure.messaging;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.domain.exception.BillingRuleException;
import com.mediflow.billing.infrastructure.config.OutpatientRefundPublicationProperties;
import org.springframework.beans.factory.annotation.Autowired;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class LedgerEventOutboxAdapter implements LedgerEventPort {
    private static final Set<String> ALLOWED_OUTPATIENT_EVENT_TYPES =
            Set.of("invoice.created", "payment.completed", "financial.clearance.granted", "payment.refunded");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final OutpatientRefundPublicationProperties refunds;
    public LedgerEventOutboxAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this(jdbc, mapper, new OutpatientRefundPublicationProperties(false, null));
    }
    @Autowired
    public LedgerEventOutboxAdapter(JdbcTemplate jdbc, ObjectMapper mapper,
            OutpatientRefundPublicationProperties refunds) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.refunds = refunds;
    }
    @Override
    public void appendHeld(UUID accountId, LedgerIntegrationEvent event) {
        insert(accountId, event, false, null);
    }

    @Override
    public void appendForOutpatientRequest(UUID accountId, UUID paymentRequestId, LedgerIntegrationEvent event) {
        List<UUID> cohort = jdbc.queryForList(
                "SELECT account_id FROM billing_outpatient_publication_cohort WHERE payment_request_id = ?",
                UUID.class, paymentRequestId);
        if (cohort.isEmpty()) {
            insert(accountId, event, false, null);
            return;
        }
        if (!accountId.equals(cohort.getFirst()) || !ALLOWED_OUTPATIENT_EVENT_TYPES.contains(event.eventType())) {
            throw new BillingRuleException("BILLING_OUTPATIENT_PUBLICATION_CONFLICT",
                    "Outpatient publication cohort mismatch for request " + paymentRequestId);
        }
        if ("payment.refunded".equals(event.eventType())) {
            if (!refunds.enabled()) {
                insert(accountId, event, false, null);
                return;
            }
            if (!(event.payload() instanceof LedgerIntegrationEvent.PaymentRefundedPayload payload)) {
                throw new BillingRuleException("BILLING_OUTPATIENT_PUBLICATION_CONFLICT", "Typed refund evidence required");
            }
            jdbc.update("""
                    INSERT INTO billing_outpatient_refund_publication
                        (refund_transaction_id,payment_request_id,acceptance_reference)
                    VALUES (?,?,?)
                    """, payload.refundTransactionId(), paymentRequestId, refunds.acceptanceReference());
        }
        insert(accountId, event, true, paymentRequestId);
    }

    private void insert(UUID accountId, LedgerIntegrationEvent event, boolean eligible, UUID outpatientPaymentRequestId) {
        try {
            String bytes = mapper.writeValueAsString(event);
            if (eligible && !Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT billing_outpatient_event_matches(?,?,?,?,?,?)", Boolean.class,
                    outpatientPaymentRequestId, accountId, event.eventId(), event.eventType(), bytes, event.version()))) {
                throw new BillingRuleException("BILLING_OUTPATIENT_PUBLICATION_CONFLICT",
                        "Outpatient event does not match persisted request " + outpatientPaymentRequestId);
            }
            jdbc.update("""
                    INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version,outpatient_payment_request_id)
                    VALUES (?,?,?,?,?,1,?)
                    """, event.eventId(), event.eventType(), accountId, bytes, eligible, outpatientPaymentRequestId);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize immutable ledger event", exception);
        }
    }
}
