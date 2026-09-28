package com.mediflow.inpatient.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.inpatient.application.dto.command.AdmissionRequestedCommand;
import com.mediflow.inpatient.application.port.in.ReactToAdmissionReferralUseCase;
import com.mediflow.inpatient.application.port.in.ReactToDepositTopupUseCase;
import com.mediflow.inpatient.application.port.in.ReactToExternalOrderUseCase;
import com.mediflow.inpatient.application.port.in.ReactToFinancialClearanceUseCase;
import com.mediflow.inpatient.application.port.in.ReactToSettlementUseCase;
import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class AdmissionRequestedContractFixtureTest {

    private static final String FIXTURE = "/contracts/admission.requested.v1.json";

    private final ReactToAdmissionReferralUseCase referrals = mock(ReactToAdmissionReferralUseCase.class);
    private final ReactToFinancialClearanceUseCase clearances = mock(ReactToFinancialClearanceUseCase.class);
    private final ReactToSettlementUseCase settlements = mock(ReactToSettlementUseCase.class);
    private final ReactToDepositTopupUseCase topups = mock(ReactToDepositTopupUseCase.class);
    private final ReactToExternalOrderUseCase externalOrders = mock(ReactToExternalOrderUseCase.class);
    private final InpatientEventConsumer consumer = new InpatientEventConsumer(new ObjectMapper(), referrals,
            clearances, settlements, topups, externalOrders);

    @Test
    void consumesCanonicalFixtureAndPassesExactAdmissionCommand() throws IOException {
        consumer.receive(new Message(readFixture(), new MessageProperties()));

        var command = ArgumentCaptor.forClass(AdmissionRequestedCommand.class);
        verify(referrals).onAdmissionRequested(command.capture());
        assertThat(command.getValue()).isEqualTo(new AdmissionRequestedCommand(
                id(1), 1, Instant.parse("2026-09-28T02:00:00Z"), id(7).toString(),
                id(2), id(3), id(4), id(5), id(6), "Community-acquired pneumonia",
                AdmissionPriority.URGENT, false, Instant.parse("2026-09-28T02:00:00Z")));
        verifyNoInteractions(clearances, settlements, topups, externalOrders);
    }

    private static UUID id(int suffix) {
        return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(suffix));
    }

    private byte[] readFixture() throws IOException {
        try (var input = getClass().getResourceAsStream(FIXTURE)) {
            assertThat(input).as("canonical admission.requested fixture").isNotNull();
            return input.readAllBytes();
        }
    }
}
