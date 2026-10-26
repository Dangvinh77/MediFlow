package com.mediflow.clinical.infrastructure.messaging;

import java.io.IOException;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.clinical.application.event.DomainEventEnvelope;

/** Serializes a version-2 outpatient event once; the returned bytes are persisted and retried unchanged. */
@Component
public class ClinicalOutpatientRegistrationEventSerializer {
    private final ObjectMapper mapper;

    public ClinicalOutpatientRegistrationEventSerializer(ObjectMapper mapper) {
        this.mapper = mapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public byte[] serialize(DomainEventEnvelope<?> event) {
        try {
            return mapper.writeValueAsBytes(event);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Clinical outpatient event could not be serialized", failure);
        }
    }
}
