package com.mediflow.report.infrastructure.persistence.adapter;

import com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.application.port.out.MedicalDischargeStorePort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.MedicalDischargeFact;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Retains only the exact five-field proof and envelope metadata, never a discharge summary. */
@Component
@Transactional(transactionManager = "reportTransactionManager", propagation = Propagation.MANDATORY)
public class MedicalDischargeStoreAdapter implements MedicalDischargeStorePort {
    private final JdbcTemplate jdbc;
    private final OperationalSnapshotCodec codec;
    public MedicalDischargeStoreAdapter(@org.springframework.beans.factory.annotation.Qualifier("reportJdbcTemplate") JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        codec = new OperationalSnapshotCodec(mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS));
    }
    @Override public void accept(DecodedCareFinanceEvent event, MedicalDischargeFact fact) {
        var m = event.metadata();
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "report-inpatient-delivery:" + m.eventId());
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM report_admission_delivery WHERE event_id=?)", Boolean.class, m.eventId())))
            throw new ReportRuleException("REPORT_MEDICAL_DISCHARGE_CONFLICT", "Delivery identity belongs to another inpatient operation");
        String payloadHash = OperationalSnapshotCodec.hash(codec.json(event.payload()));
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", m.eventId().toString()); envelope.put("occurredAt", m.occurredAt().toString());
        envelope.put("correlationId", m.correlationId()); envelope.put("producer", m.producer());
        envelope.put("eventType", m.eventType()); envelope.put("version", m.version()); envelope.put("payload", event.payload());
        String envelopeHash = OperationalSnapshotCodec.hash(codec.json(envelope));
        jdbc.update("INSERT INTO report_medical_discharge_delivery(event_id,admission_id,envelope_hash) VALUES (?,?,?) ON CONFLICT DO NOTHING",
                m.eventId(), fact.admissionId(), envelopeHash);
        same(envelopeHash, jdbc.queryForObject("SELECT envelope_hash FROM report_medical_discharge_delivery WHERE event_id=?", String.class, m.eventId()));
        try { jdbc.update("""
                INSERT INTO report_medical_discharge(admission_id,patient_id,summary_id,approved_by,approved_at_iso,
                    first_event_id,occurred_at_iso,correlation_id,payload_hash)
                VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(admission_id) DO NOTHING
                """, fact.admissionId(), fact.patientId(), fact.summaryId(), fact.approvedBy(), event.payload().get("approvedAt"),
                m.eventId(), m.occurredAt().toString(), m.correlationId(), payloadHash);
        } catch (org.springframework.dao.DuplicateKeyException conflict) {
            throw new ReportRuleException("REPORT_MEDICAL_DISCHARGE_CONFLICT", "Medical proof identity belongs to another source");
        }
        same(payloadHash, jdbc.queryForObject("SELECT payload_hash FROM report_medical_discharge WHERE admission_id=?", String.class, fact.admissionId()));
    }
    @Override public Optional<DecodedCareFinanceEvent> find(UUID admissionId) {
        var rows = jdbc.queryForList("SELECT * FROM report_medical_discharge WHERE admission_id=? AND status<>'REJECTED'", admissionId);
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.getFirst();
        Map<String, Object> payload = Map.of("admissionId", admissionId.toString(), "patientId", row.get("patient_id").toString(),
                "summaryId", row.get("summary_id").toString(), "approvedBy", row.get("approved_by").toString(),
                "approvedAt", row.get("approved_at_iso"));
        same((String) row.get("payload_hash"), OperationalSnapshotCodec.hash(codec.json(payload)));
        var metadata = new CareFinanceEventMetadata((UUID)row.get("first_event_id"),
                "discharge.medically.approved", 1, Instant.parse((String)row.get("occurred_at_iso")),
                (String)row.get("correlation_id"), "inpatient-service", "admissionId", admissionId);
        var envelope = new LinkedHashMap<String, Object>();
        envelope.put("eventId", metadata.eventId().toString()); envelope.put("occurredAt", metadata.occurredAt().toString());
        envelope.put("correlationId", metadata.correlationId()); envelope.put("producer", metadata.producer());
        envelope.put("eventType", metadata.eventType()); envelope.put("version", metadata.version()); envelope.put("payload", payload);
        same(OperationalSnapshotCodec.hash(codec.json(envelope)), jdbc.queryForObject(
                "SELECT envelope_hash FROM report_medical_discharge_delivery WHERE event_id=?", String.class, metadata.eventId()));
        return Optional.of(new DecodedCareFinanceEvent(metadata, payload));
    }
    @Override public void markApplied(UUID admissionId) {
        jdbc.update("UPDATE report_medical_discharge SET status='APPLIED' WHERE admission_id=? AND status='PENDING_START'", admissionId);
    }
    @Override public boolean pending(UUID admissionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM report_medical_discharge WHERE admission_id=? AND status='PENDING_START')", Boolean.class, admissionId));
    }
    @Override public void reject(UUID admissionId) {
        jdbc.update("UPDATE report_medical_discharge SET status='REJECTED' WHERE admission_id=? AND status='PENDING_START'", admissionId);
    }
    private static void same(String expected, String actual) {
        if (!expected.equals(actual)) throw new ReportRuleException("REPORT_MEDICAL_DISCHARGE_CONFLICT", "Immutable discharge evidence differs");
    }
}
