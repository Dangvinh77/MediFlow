package com.mediflow.billing.application.service;

import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.billing.application.port.out.FinancialClearanceAuthorityPort;
import com.mediflow.billing.domain.model.FinancialClearanceAuthority;
import com.mediflow.common.api.ApiResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FinancialClearanceLookupServiceTest {
    private static final Instant NOW=Instant.parse("2026-10-06T08:00:00Z");
    private static final UUID ID=UUID.fromString("00000000-0000-0000-0000-000000000046");
    private final FinancialClearanceAuthorityPort repository=mock(FinancialClearanceAuthorityPort.class);
    private final LookupFinancialClearanceService service=new LookupFinancialClearanceService(repository,Clock.fixed(NOW,ZoneOffset.UTC));
    private final ObjectMapper mapper=new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @ParameterizedTest @ValueSource(strings={"active","inactive","missing"})
    void lookup_producerFixturesMatchActualUseCaseOutput(String state) throws Exception {
        try(var stream=getClass().getResourceAsStream("/contracts/clearance-authority-v1/"+state+".json")) {
            assertThat(stream).isNotNull();
            JsonNode fixture=mapper.readTree(stream), data=fixture.path("data");
            when(repository.find(ID)).thenReturn(state.equals("missing") ? Optional.empty() : Optional.of(
                    new FinancialClearanceAuthority(ID,id(data,"invoiceId"),id(data,"accountId"),id(data,"patientId"),
                            "SURGERY","ADMISSION",id(data,"careEpisodeId"),id(data,"admissionId"),id(data,"surgeryCaseId"),
                            Instant.parse(data.path("grantedAt").asText()),null,null,state.equals("active"),NOW)));
            var actual=mapper.<JsonNode>valueToTree(ApiResponse.ok(service.lookup(ID),fixture.path("correlationId").asText()));
            assertThat(actual.path("data")).isEqualTo(data);
            assertThat(actual.path("success")).isEqualTo(fixture.path("success"));
            assertThat(actual.path("error")).isEqualTo(fixture.path("error"));
            assertThat(actual.path("correlationId")).isEqualTo(fixture.path("correlationId"));
        }
    }
    @ParameterizedTest @ValueSource(strings={"before-grant","at-expiry","revoked","ledger-denied","active"})
    void lookup_exclusiveExpiryRevocationAndLedgerStateAreIndependentGuards(String state) {
        Instant granted=state.equals("before-grant")?NOW.plusSeconds(1):NOW.minusSeconds(60);
        Instant expires=state.equals("at-expiry")?NOW:NOW.plusSeconds(60);
        when(repository.find(ID)).thenReturn(Optional.of(new FinancialClearanceAuthority(ID,UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),"SURGERY","OUTPATIENT_VISIT",UUID.randomUUID(),null,UUID.randomUUID(),granted,expires,
                state.equals("revoked")?NOW:null,!state.equals("ledger-denied"),NOW)));
        assertThat(service.lookup(ID).eligible()).isEqualTo(state.equals("active"));
    }
    private static UUID id(JsonNode node,String field) { return UUID.fromString(node.path(field).asText()); }
}
