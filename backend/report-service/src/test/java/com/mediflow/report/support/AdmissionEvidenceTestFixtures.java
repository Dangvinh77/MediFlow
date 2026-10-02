package com.mediflow.report.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.report.application.dto.command.carefinance.CareFinanceEventMetadata;
import com.mediflow.report.application.dto.command.carefinance.DecodedCareFinanceEvent;
import com.mediflow.report.infrastructure.messaging.carefinance.CareFinanceEnvelopeDecoder;

/** Reads the producer's actual bytes without imports/dependencies on another service's Java classes. */
public final class AdmissionEvidenceTestFixtures {
    private AdmissionEvidenceTestFixtures() { }

    public static DecodedCareFinanceEvent fixture(String routingKey) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("backend/report-service/pom.xml"))) current = current.getParent();
        if (current == null) throw new IOException("Repository root not found");
        byte[] bytes = Files.readAllBytes(current.resolve("backend/inpatient-service/src/test/resources/contracts/" + routingKey + ".v1.json"));
        return new CareFinanceEnvelopeDecoder(new ObjectMapper().findAndRegisterModules()).decode(routingKey, bytes);
    }

    public static DecodedCareFinanceEvent changed(DecodedCareFinanceEvent event, UUID eventId, String field, Object value) {
        var payload = new LinkedHashMap<>(event.payload());
        if (field != null) payload.put(field, value);
        var m = event.metadata();
        return new DecodedCareFinanceEvent(new CareFinanceEventMetadata(eventId, m.eventType(), m.version(),
                m.occurredAt(), m.correlationId(), m.producer(), m.sourceField(), m.sourceId()), payload);
    }
}
