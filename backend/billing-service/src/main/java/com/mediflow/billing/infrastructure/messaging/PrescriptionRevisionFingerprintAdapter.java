package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.PrescriptionRevisionCommand;
import com.mediflow.billing.application.port.out.PrescriptionRevisionFingerprintPort;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Canonical hashes shared by the preflight API and the Pharmacy revised-event consumer. */
@Component
public class PrescriptionRevisionFingerprintAdapter implements PrescriptionRevisionFingerprintPort {
    private final ObjectMapper mapper;
    /** Configured like {@link PrescriptionChargeDecoder}: reading through a tree normalizes decimals identically. */
    private final ObjectMapper wireMapper;

    public PrescriptionRevisionFingerprintAdapter(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.wireMapper = mapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    @Override public String payloadFingerprint(PrescriptionRevisionCommand command) {
        var payload = snapshotFields(command);
        payload.put("operationId", command.operationId().toString());
        payload.put("expectedRevision", command.expectedRevision());
        payload.put("revision", command.revision());
        return hash(payload);
    }

    /**
     * Must equal the snapshot hash {@link PrescriptionChargeDecoder} derives from Pharmacy's created/terminal payloads,
     * so the canonical fields are round-tripped through the same JSON tree to Map path before hashing.
     */
    @Override public String snapshotFingerprint(PrescriptionRevisionCommand command) {
        try {
            var tree = wireMapper.readTree(wireMapper.writeValueAsBytes(snapshotFields(command)));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(wireMapper.writeValueAsBytes(wireMapper.convertValue(tree, Map.class))));
        } catch (Exception impossible) {
            throw new IllegalStateException("Unable to fingerprint prescription revision snapshot", impossible);
        }
    }

    private TreeMap<String, Object> snapshotFields(PrescriptionRevisionCommand command) {
        var fields = new TreeMap<String, Object>();
        fields.put("prescriptionId", command.prescriptionId().toString());
        // Mirror Pharmacy's NON_NULL wire payload exactly, so terminal events after a revision prove this snapshot.
        if (command.recordId() != null) fields.put("recordId", command.recordId().toString());
        fields.put("patientId", command.patientId().toString());
        fields.put("departmentId", command.departmentId().toString());
        fields.put("careContext", "OUTPATIENT");
        fields.put("careEpisodeType", command.careEpisodeType());
        fields.put("careEpisodeId", command.careEpisodeId().toString());
        fields.put("sourceType", "PRESCRIPTION");
        fields.put("sourceId", command.prescriptionId().toString());
        fields.put("priceCode", command.priceCode());
        var items = new ArrayList<Map<String, Object>>();
        command.items().forEach(item -> {
            var line = new TreeMap<String, Object>();
            line.put("drugId", item.drugId().toString());
            line.put("drugName", item.drugName());
            line.put("quantity", item.quantity());
            line.put("unitPrice", item.unitPrice());
            items.add(line);
        });
        fields.put("items", items);
        fields.put("totalAmount", command.totalAmount());
        return fields;
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(value)));
        } catch (Exception impossible) {
            throw new IllegalStateException("Unable to fingerprint prescription revision", impossible);
        }
    }
}
