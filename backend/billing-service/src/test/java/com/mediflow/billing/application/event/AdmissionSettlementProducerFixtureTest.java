package com.mediflow.billing.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.billing.application.dto.request.SettleAdmissionRequest;
import com.mediflow.billing.application.port.out.AdmissionSettlementRepositoryPort;
import com.mediflow.billing.application.port.out.LedgerEventPort;
import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.application.service.AdmissionSettlementService;
import com.mediflow.billing.domain.model.AccountStatus;
import com.mediflow.billing.domain.model.BillingAccount;
import com.mediflow.billing.domain.model.CareEpisodeType;
import com.mediflow.billing.domain.model.Settlement;

/** Checks actual producer service output; Inpatient reads this file without copying it. */
class AdmissionSettlementProducerFixtureTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Test
    void settlementCompletedFixture_matchesActualProducerPayload() throws Exception {
        var fixture = fixture("settlement-completed");
        var payload = mapper.treeToValue(fixture.path("payload"), LedgerIntegrationEvent.SettlementCompletedPayload.class);

        var account = BillingAccount.restore(payload.accountId(), payload.patientId(), payload.departmentId(),
                CareEpisodeType.ADMISSION, payload.admissionId(), AccountStatus.CHARGE_CLOSED, "VND", 0,
                payload.completedAt(), payload.completedAt(), null, payload.completedAt(), payload.completedAt());
        var context = new AdmissionSettlementRepositoryPort.SettlementContext(payload.grossAmount(), payload.completedPayments(),
                payload.completedRefunds(), payload.insuranceAmount(), List.of(), 1, null);
        var repository = mock(AdmissionSettlementRepositoryPort.class);
        var events = mock(LedgerEventPort.class);
        when(repository.lockAccountById(account.getAccountId())).thenReturn(account);
        when(repository.loadContext(account.getAccountId())).thenReturn(context);
        when(repository.saveSettlement(any())).thenAnswer(call -> {
            Settlement s = call.getArgument(0);
            return Settlement.restore(payload.settlementId(), s.getAccountId(), s.getAdmissionId(), s.getSettlementVersion(),
                    s.getSupersedesSettlementId(), s.getGrossAmount(), s.getInsuranceAmount(), s.getPatientLiability(),
                    s.getCompletedPayments(), s.getCompletedRefunds(), s.getBalance(), s.getOutcome(), s.getCompletedAt());
        });

        new AdmissionSettlementService(mock(ProcessedEventPort.class), repository, events,
                Clock.fixed(payload.completedAt(), ZoneOffset.UTC))
                .settle(account.getAccountId(), new SettleAdmissionRequest(null, null, null),
                        id(9), fixture.path("correlationId").asText());

        var capture = ArgumentCaptor.forClass(LedgerIntegrationEvent.class);
        verify(events).appendHeld(eq(account.getAccountId()), capture.capture());
        var emitted = capture.getValue();
        assertThat(mapper.<JsonNode>valueToTree(emitted.payload())).isEqualTo(fixture.path("payload"));
        assertThat(emitted.eventType()).isEqualTo(fixture.path("eventType").asText());
        assertThat(emitted.version()).isEqualTo(1);
        assertThat(emitted.producer()).isEqualTo("billing-service");
        assertThat(emitted.correlationId()).isEqualTo(fixture.path("correlationId").asText());
    }

    private JsonNode fixture(String name) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/contracts/ledger-v1/" + name + ".json")) {
            assertThat(stream).isNotNull();
            return mapper.readTree(stream);
        }
    }
    private static UUID id(int suffix) { return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", suffix)); }
}
