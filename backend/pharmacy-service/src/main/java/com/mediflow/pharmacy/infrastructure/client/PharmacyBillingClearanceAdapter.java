package com.mediflow.pharmacy.infrastructure.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.application.port.out.PrescriptionClearanceAuthorityPort;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

/** Mirrors Billing's live lookup DTO. No prescription target is invented from its response. */
public final class PharmacyBillingClearanceAdapter implements PrescriptionClearanceAuthorityPort {
    private final PharmacyBillingClearanceFeignClient client;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SecretKey key;
    public PharmacyBillingClearanceAdapter(PharmacyBillingClearanceFeignClient client, ObjectMapper mapper,
            Clock clock, JwtProperties jwt) {
        this.client=client; this.clock=clock;
        this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        key=Keys.hmacShaKeyFor(jwt.secret().getBytes(StandardCharsets.UTF_8));
    }
    @Override public Observation lookup(UUID id,String correlation) {
        if(id==null || correlation==null || correlation.isBlank() || correlation.length()>120
                || correlation.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Bounded lookup identity required");
        try {
            Instant issued=clock.instant().truncatedTo(ChronoUnit.SECONDS);
            String token=Jwts.builder().subject("pharmacy-service").claim("type","service").claim("role","SYSTEM")
                    .issuedAt(Date.from(issued)).expiration(Date.from(issued.plusSeconds(60))).signWith(key).compact();
            var response=client.lookup(id,"Bearer "+token,correlation);
            if(response==null || !response.getStatusCode().is2xxSuccessful() || response.getBody()==null
                    || response.getBody().getBytes(StandardCharsets.UTF_8).length>65536
                    || !correlation.equals(response.getHeaders().getFirst("X-Correlation-Id"))) throw new IllegalArgumentException();
            var root=mapper.readTree(response.getBody());
            if(root==null || !root.isObject() || !bool(root,"success") || !root.has("error") || !root.get("error").isNull()
                    || !correlation.equals(text(root,"correlationId"))) throw new IllegalArgumentException();
            var data=root.get("data");
            if(data==null || !data.isObject() || !id.equals(uuid(data,"clearanceId"))) throw new IllegalArgumentException();
            boolean exists=bool(data,"exists"),eligible=bool(data,"eligible");
            Instant observed=Instant.parse(text(data,"observedAt")),now=clock.instant();
            if(observed.isBefore(now.minusSeconds(5)) || observed.isAfter(now.plusSeconds(2))) throw new IllegalArgumentException();
            if(!exists) {
                if(eligible) throw new IllegalArgumentException();
                for(String field:new String[]{"invoiceId","accountId","patientId","purpose","careEpisodeType","careEpisodeId",
                        "admissionId","surgeryCaseId","grantedAt","expiresAt"}) requireNull(data,field);
                return new Observation(false,id,false,null,null,null,null,null,null,null,null,null,null,observed);
            }
            var granted=Instant.parse(text(data,"grantedAt"));
            var expires=nullableInstant(data,"expiresAt");
            if(granted.isAfter(observed) || expires!=null && !expires.isAfter(granted)) throw new IllegalArgumentException();
            return new Observation(true,id,eligible,uuid(data,"invoiceId"),uuid(data,"accountId"),uuid(data,"patientId"),
                    text(data,"purpose"),text(data,"careEpisodeType"),uuid(data,"careEpisodeId"),nullableUuid(data,"admissionId"),
                    nullableUuid(data,"surgeryCaseId"),granted,expires,observed);
        } catch(Exception unavailable) { throw new PharmacyUpstreamUnavailableException(); }
    }
    private static void requireNull(JsonNode data,String field){if(!data.has(field)||!data.get(field).isNull())throw new IllegalArgumentException();}
    private static String text(JsonNode data,String field){var n=data.get(field);if(n==null||!n.isTextual()||n.textValue().isBlank())throw new IllegalArgumentException();return n.textValue();}
    private static boolean bool(JsonNode data,String field){var n=data.get(field);if(n==null||!n.isBoolean())throw new IllegalArgumentException();return n.booleanValue();}
    private static UUID uuid(JsonNode data,String field){String value=text(data,field);var id=UUID.fromString(value);if(!id.toString().equals(value))throw new IllegalArgumentException();return id;}
    private static UUID nullableUuid(JsonNode data,String field){if(!data.has(field))throw new IllegalArgumentException();return data.get(field).isNull()?null:uuid(data,field);}
    private static Instant nullableInstant(JsonNode data,String field){if(!data.has(field))throw new IllegalArgumentException();return data.get(field).isNull()?null:Instant.parse(text(data,field));}
}
