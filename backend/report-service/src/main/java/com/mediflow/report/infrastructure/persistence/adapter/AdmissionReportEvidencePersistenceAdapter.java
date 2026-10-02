package com.mediflow.report.infrastructure.persistence.adapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.port.out.AdmissionReportEvidencePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.AdmissionReportFact;
import com.mediflow.report.domain.model.AdmissionReportFact.Kind;
import com.mediflow.report.domain.model.AdmissionReportHistory;

/** Minimal local facts plus envelope hash. No complete producer/clinical payload retention. */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class AdmissionReportEvidencePersistenceAdapter implements AdmissionReportEvidencePort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AdmissionReportEvidencePersistenceAdapter(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override
    public void lockAdmission(UUID admissionId) {
        jdbc.update("INSERT INTO report_admission_target(admission_id) VALUES (?) ON CONFLICT DO NOTHING", admissionId);
        jdbc.queryForObject("SELECT admission_id FROM report_admission_target WHERE admission_id = ? FOR UPDATE", UUID.class, admissionId);
    }

    @Override
    public boolean claimDelivery(DecodedCareFinanceEvent event) {
        var metadata = event.metadata();
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", metadata.eventId());
        envelope.put("eventType", metadata.eventType());
        envelope.put("version", metadata.version());
        envelope.put("occurredAt", metadata.occurredAt().toString());
        envelope.put("correlationId", metadata.correlationId());
        envelope.put("producer", metadata.producer());
        envelope.put("payload", event.payload());
        String fingerprint = fingerprint(envelope);
        int inserted = jdbc.update("""
                INSERT INTO report_admission_delivery(event_id, admission_id, event_type, envelope_fingerprint)
                VALUES (?, ?, ?, ?) ON CONFLICT (event_id) DO NOTHING
                """, metadata.eventId(), metadata.sourceId(), metadata.eventType(), fingerprint);
        String existing = jdbc.queryForObject("SELECT envelope_fingerprint FROM report_admission_delivery WHERE event_id = ?",
                String.class, metadata.eventId());
        if (!fingerprint.equals(existing)) throw new ReportRuleException("REPORT_ADMISSION_EVENT_CONFLICT", "Admission envelope changed");
        return inserted == 1;
    }

    @Override
    public AdmissionReportHistory findHistory(UUID admissionId) {
        var facts = jdbc.query("SELECT * FROM report_admission_fact WHERE admission_id = ? ORDER BY fact_type",
                (row, index) -> new AdmissionReportFact(Kind.valueOf(row.getString("fact_type")),
                        row.getObject("admission_id", UUID.class), row.getObject("patient_id", UUID.class),
                        row.getObject("department_id", UUID.class), row.getObject("bed_id", UUID.class),
                        Instant.parse(row.getString("business_at_iso")), row.getBoolean("emergency"),
                        row.getObject("emergency_override_id", UUID.class), row.getObject("settlement_id", UUID.class),
                        row.getObject("approved_override_id", UUID.class)), admissionId);
        var history = new AdmissionReportHistory(admissionId, null, null);
        for (var fact : facts) history = history.apply(fact);
        return history;
    }

    @Override
    public void store(AdmissionReportFact fact) {
        jdbc.update("""
                INSERT INTO report_admission_fact(admission_id, fact_type, patient_id, department_id, bed_id,
                    business_at, business_at_iso, emergency, emergency_override_id, settlement_id, approved_override_id)
                VALUES (?, ?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), ?, ?, ?, ?, ?) ON CONFLICT (admission_id, fact_type) DO NOTHING
                """, fact.admissionId(), fact.kind().name(), fact.patientId(), fact.departmentId(), fact.bedId(),
                fact.businessAt().toString(), fact.businessAt().toString(), fact.emergency(), fact.emergencyOverrideId(), fact.settlementId(), fact.approvedOverrideId());
        var history = findHistory(fact.admissionId());
        var stored = fact.kind() == Kind.STARTED ? history.start() : history.close();
        if (!fact.equals(stored)) throw new ReportRuleException("REPORT_ADMISSION_SOURCE_CONFLICT", "Immutable admission evidence differs");
    }

    private String fingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Admission envelope cannot be normalized", exception);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
