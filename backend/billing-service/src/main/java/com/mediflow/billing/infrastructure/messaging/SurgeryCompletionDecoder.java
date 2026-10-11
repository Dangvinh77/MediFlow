package com.mediflow.billing.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mediflow.billing.application.dto.command.CaptureSurgeryCompletionCommand;
import com.mediflow.billing.application.event.SurgeryCompletedEvent;
import com.mediflow.billing.application.port.out.SurgeryCompletionWirePort;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pins sourceRevision 1 and exact original bytes; never accepts a partial compatibility decode. */
public final class SurgeryCompletionDecoder implements SurgeryCompletionWirePort {
    private static final Set<String> ENVELOPE=Set.of("eventId","eventType","version","occurredAt","correlationId","producer","payload");
    private static final Set<String> PAYLOAD=Set.of("surgeryCaseId","surgeryRequestId","patientId","departmentId","careEpisodeType",
            "careEpisodeId","admissionId","recordId","caseRevision","resultId","sourceRevision","procedureCode","methodCode",
            "outcomeCode","complicationsCategory","complicationsSummary","performedItems","startedAt","completedAt","recordedAt");
    private static final Set<String> ITEM=Set.of("performedItemId","itemCode","priceCode","quantity");
    private final ObjectMapper mapper;
    public SurgeryCompletionDecoder(ObjectMapper mapper) {
        this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }
    @Override public CaptureSurgeryCompletionCommand decode(String key,byte[] bytes) {
        try {
            if (!"surgery.completed".equals(key) || bytes==null || bytes.length==0 || bytes.length>1_048_576)throw new IllegalArgumentException();
            var root=mapper.readTree(bytes); exact(root,ENVELOPE); var payload=root.get("payload"); exact(payload,PAYLOAD);
            if (!key.equals(text(root,"eventType")) || !"surgery-service".equals(text(root,"producer"))
                    || number(root,"version")!=1 || number(payload,"sourceRevision")!=1)throw new IllegalArgumentException();
            uuid(root,"eventId");
            for(var field:Set.of("surgeryCaseId","surgeryRequestId","patientId","departmentId","careEpisodeId","resultId"))uuid(payload,field);
            for(var field:Set.of("admissionId","recordId"))if(!payload.path(field).isNull())uuid(payload,field);
            for(var field:Set.of("procedureCode","methodCode","outcomeCode"))code(payload,field);
            for(var field:Set.of("complicationsCategory","complicationsSummary")) {
                var value=payload.path(field); if(!value.isNull() && (!value.isTextual() || value.textValue().length()>2000))throw new IllegalArgumentException();
            }
            var items=payload.path("performedItems"); if(!items.isArray() || items.isEmpty() || items.size()>1000)throw new IllegalArgumentException();
            var ids=new HashSet<UUID>(); var codes=new HashSet<String>();
            for(var item:items) {
                exact(item,ITEM); if(!ids.add(uuid(item,"performedItemId")) || !codes.add(code(item,"itemCode")))throw new IllegalArgumentException();
                code(item,"priceCode"); var value=item.path("quantity");
                if(!value.isNumber())throw new IllegalArgumentException(); var quantity=value.decimalValue();
                if(quantity.signum()<=0 || quantity.stripTrailingZeros().scale()>4 || quantity.precision()-quantity.scale()>15)throw new IllegalArgumentException();
            }
            return new CaptureSurgeryCompletionCommand(mapper.treeToValue(root,SurgeryCompletedEvent.class),uuid(payload,"surgeryRequestId"),
                    text(payload,"careEpisodeType"),uuid(payload,"careEpisodeId"),number(payload,"caseRevision"),number(payload,"sourceRevision"),
                    text(payload,"completedAt"),text(payload,"recordedAt"),hash(payload),hash(root));
        }catch(Exception invalid){throw new IllegalArgumentException("Invalid Surgery completed V1 contract");}
    }
    private String hash(JsonNode node)throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(mapper.convertValue(node,Map.class))));
    }
    private static void exact(JsonNode node,Set<String> fields) {
        if(node==null || !node.isObject() || node.size()!=fields.size())throw new IllegalArgumentException();
        node.fieldNames().forEachRemaining(field->{if(!fields.contains(field))throw new IllegalArgumentException();});
    }
    private static String text(JsonNode node,String field) {
        var value=node.path(field); if(!value.isTextual() || value.textValue().isBlank())throw new IllegalArgumentException(); return value.textValue();
    }
    private static String code(JsonNode node,String field) {
        String value=text(node,field); if(!value.matches("[A-Za-z0-9._-]{1,64}"))throw new IllegalArgumentException(); return value;
    }
    private static UUID uuid(JsonNode node,String field) {
        String value=text(node,field); UUID id=UUID.fromString(value); if(!id.toString().equals(value))throw new IllegalArgumentException(); return id;
    }
    private static int number(JsonNode node,String field) {
        var value=node.path(field); if(!value.isIntegralNumber() || !value.canConvertToInt())throw new IllegalArgumentException(); return value.intValue();
    }
}
