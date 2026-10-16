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

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class LedgerEventOutboxAdapter implements LedgerEventPort {
    private static final Set<String> ALLOWED_OUTPATIENT_EVENT_TYPES =
            Set.of("invoice.created", "payment.completed", "financial.clearance.granted");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public LedgerEventOutboxAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
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
        insert(accountId, event, true, paymentRequestId);
    }

    private void insert(UUID accountId, LedgerIntegrationEvent event, boolean eligible, UUID outpatientPaymentRequestId) {
        try {
            jdbc.update("""
                    INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,contract_version,outpatient_payment_request_id)
                    VALUES (?,?,?,?,?,1,?)
                    """, event.eventId(), event.eventType(), accountId, mapper.writeValueAsString(event), eligible, outpatientPaymentRequestId);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize immutable ledger event", exception);
        }
    }
}
