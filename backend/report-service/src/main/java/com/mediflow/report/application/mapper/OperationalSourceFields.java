package com.mediflow.report.application.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

final class OperationalSourceFields {
    private OperationalSourceFields() { }
    static String text(Map<String,Object> payload,String key) {
        if (!(payload.get(key) instanceof String value) || value.isBlank()) throw invalid(key);
        return value;
    }
    static UUID uuid(Map<String,Object> payload,String key) {
        String text=text(payload,key);
        UUID id=UUID.fromString(text);
        if (!id.toString().equals(text)) throw invalid(key);
        return id;
    }
    static Instant instant(Map<String,Object> payload,String key) { return Instant.parse(text(payload,key)); }
    static long positiveInteger(Object value,String key) {
        if (!(value instanceof Integer || value instanceof Long || value instanceof java.math.BigInteger)) throw invalid(key);
        long integer=new BigDecimal(value.toString()).longValueExact();
        if(integer<1) throw invalid(key);
        return integer;
    }
    static void episode(Map<String,Object> payload) {
        String type=text(payload,"careEpisodeType");
        UUID id=uuid(payload,"careEpisodeId");
        uuid(payload,"patientId");
        if(type.equals("ADMISSION")) {
            if(!id.equals(uuid(payload,"admissionId"))) throw invalid("admissionId");
        } else if(!type.equals("OUTPATIENT_VISIT") || payload.get("admissionId")!=null) throw invalid("careEpisodeType");
    }
    static IllegalArgumentException invalid(String key) { return new IllegalArgumentException("Invalid operational source field: "+key); }
}
