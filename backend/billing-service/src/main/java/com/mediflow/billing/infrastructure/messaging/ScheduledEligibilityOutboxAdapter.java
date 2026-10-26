package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.billing.application.event.OutpatientFinancialEligibilityGrantedEvent;
import com.mediflow.billing.application.port.out.ScheduledEligibilityEventPort;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** New publication authority is captured at insert time; old held bytes never get promoted. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class ScheduledEligibilityOutboxAdapter implements ScheduledEligibilityEventPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final boolean publicationEnabled;
    private final String acceptanceReference;

    public ScheduledEligibilityOutboxAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this(jdbc, mapper, false, "");
    }

    @Autowired
    public ScheduledEligibilityOutboxAdapter(JdbcTemplate jdbc, ObjectMapper mapper,
            @Value("${mediflow.billing.scheduled-eligibility-publication.enabled:false}") boolean publicationEnabled,
            @Value("${mediflow.billing.scheduled-eligibility-publication.acceptance-reference:}") String acceptanceReference) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        if (publicationEnabled && (acceptanceReference == null || acceptanceReference.isBlank()
                || acceptanceReference.length() > 200))
            throw new IllegalArgumentException("Scheduled eligibility publication requires an acceptance reference");
        this.publicationEnabled = publicationEnabled;
        this.acceptanceReference = acceptanceReference;
    }

    @Override
    public void appendHeld(OutpatientFinancialEligibilityGrantedEvent event) {
        if (!"outpatient.financial.eligibility.granted".equals(event.eventType()) || event.version() != 1
                || event.payload() == null || event.payload().basis() == null
                || !("PATIENT_PAYMENT".equals(event.payload().basis())
                    || "ZERO_LIABILITY".equals(event.payload().basis()))) {
            throw new IllegalArgumentException("Invalid outpatient eligibility event");
        }
        try {
            jdbc.update("""
                    INSERT INTO BILLING_EVENT_OUTBOX(event_id,routing_key,aggregate_id,payload,publication_enabled,
                        contract_version,outpatient_payment_request_id,scheduling_payment_operation_id,scheduling_acceptance_reference)
                    VALUES (?,?,?,?,?,1,NULL,?,?)
                    """, event.eventId(), event.eventType(), event.payload().registrationId(),
                    mapper.writeValueAsString(event), publicationEnabled,
                    publicationEnabled ? event.payload().paymentOperationId() : null,
                    publicationEnabled ? acceptanceReference : null);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize outpatient eligibility event", exception);
        }
    }
}
