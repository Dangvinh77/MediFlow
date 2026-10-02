package com.mediflow.pharmacy.infrastructure.messaging;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent;

/** V1 serializer/decoder for offline checks and held outbox bytes; no live producer switch or V0 fallback. */
@Component
public class PrescriptionCareEventCodec {
    private final ObjectMapper mapper;

    public PrescriptionCareEventCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public byte[] encode(PrescriptionCareEvent event) {
        java.util.Objects.requireNonNull(event, "event");
        try {
            return mapper.writeValueAsBytes(event);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Cannot encode Pharmacy V1 event", exception);
        }
    }

    public PrescriptionCareEvent decode(String routingKey, byte[] body) {
        try {
            var event = mapper.readValue(body, PrescriptionCareEvent.class);
            if (!event.eventType().routingKey().equals(routingKey)) {
                throw new IllegalArgumentException("Prescription eventType must match its routing key");
            }
            return event;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid Pharmacy V1 event", exception);
        }
    }
}
