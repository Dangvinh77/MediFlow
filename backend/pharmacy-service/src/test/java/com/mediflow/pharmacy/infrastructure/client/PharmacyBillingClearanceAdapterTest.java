package com.mediflow.pharmacy.infrastructure.client;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException;
import com.mediflow.pharmacy.infrastructure.security.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;

class PharmacyBillingClearanceAdapterTest {
    private static final String SECRET="billing-clearance-client-test-secret-at-least-32-bytes";
    private static final String CORRELATION="00000000-0000-0000-0000-000000000009";
    private static final UUID ID=UUID.fromString("00000000-0000-0000-0000-000000000046");
    private static final Instant NOW=Instant.parse("2026-10-06T08:00:00Z");
    private final ObjectMapper mapper=new ObjectMapper();
    private final PharmacyBillingClearanceFeignClient client=mock(PharmacyBillingClearanceFeignClient.class);
    private final PharmacyBillingClearanceAdapter adapter=new PharmacyBillingClearanceAdapter(client,mapper,
            Clock.fixed(NOW,ZoneOffset.UTC),new JwtProperties(SECRET));

    @ParameterizedTest @ValueSource(strings={"active","inactive","missing"})
    void preservesActualBillingFixtureWithoutPretendingSurgeryGrantIsPrescriptionPermission(String name)throws Exception {
        byte[] bytes=Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/clearance-authority-v1/"+name+".json"));
        when(client.lookup(eq(ID),anyString(),eq(CORRELATION))).thenAnswer(call->{
            var claims=Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .clock(()->Date.from(NOW)).build().parseSignedClaims(call.getArgument(1,String.class).substring(7)).getPayload();
            assertThat(claims.getSubject()).isEqualTo("pharmacy-service");
            assertThat(claims).containsEntry("role","SYSTEM").containsEntry("type","service");
            assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(60));
            return ResponseEntity.ok().header("X-Correlation-Id",CORRELATION).body(new String(bytes,StandardCharsets.UTF_8));
        });
        var actual=adapter.lookup(ID,CORRELATION); var source=mapper.readTree(bytes).get("data");
        assertThat(actual.exists()).isEqualTo(source.get("exists").booleanValue());
        assertThat(actual.eligible()).isEqualTo(source.get("eligible").booleanValue());
        if(actual.exists()) assertThat(actual.purpose()).isEqualTo("SURGERY");
    }

    @ParameterizedTest @ValueSource(strings={"wrongId","stale","future","textEligible","missingField","trailing","duplicate","wrongCorrelation","serverError"})
    void malformedUncorrelatedOrUnavailableEvidenceFailsClosed(String mutation)throws Exception {
        var root=(ObjectNode)mapper.readTree(Files.readAllBytes(Path.of("../billing-service/src/test/resources/contracts/clearance-authority-v1/active.json")));
        var data=(ObjectNode)root.get("data");
        switch(mutation){case "wrongId"->data.put("clearanceId",UUID.randomUUID().toString());
            case "stale"->data.put("observedAt",NOW.minusSeconds(6).toString());
            case "future"->data.put("observedAt",NOW.plusSeconds(3).toString());
            case "textEligible"->data.put("eligible","true");case "missingField"->data.remove("expiresAt");
            case "wrongCorrelation"->root.put("correlationId","other");default->{}}
        String body=mapper.writeValueAsString(root);
        if(mutation.equals("trailing"))body+=" {}";
        if(mutation.equals("duplicate"))body=body.replace("\"eligible\":true","\"eligible\":true,\"eligible\":false");
        when(client.lookup(eq(ID),anyString(),eq(CORRELATION))).thenReturn(ResponseEntity.status(mutation.equals("serverError")?503:200)
                .header("X-Correlation-Id",CORRELATION).body(body));
        assertThatThrownBy(()->adapter.lookup(ID,CORRELATION)).isInstanceOf(PharmacyUpstreamUnavailableException.class)
                .hasMessageNotContaining(SECRET);
    }
}
