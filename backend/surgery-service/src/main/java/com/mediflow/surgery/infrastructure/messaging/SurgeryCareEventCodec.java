package com.mediflow.surgery.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.surgery.application.event.SurgeryCareEvent;
import org.springframework.stereotype.Component;

/** One serializer for fixture tests and immutable held outbound bytes. */
@Component
public class SurgeryCareEventCodec {
    private final ObjectMapper mapper;
    public SurgeryCareEventCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    public byte[] encode(SurgeryCareEvent event) {
        try { return mapper.writeValueAsBytes(java.util.Objects.requireNonNull(event)); }
        catch (Exception invalid) { throw new IllegalArgumentException("Cannot encode Surgery event", invalid); }
    }
}
