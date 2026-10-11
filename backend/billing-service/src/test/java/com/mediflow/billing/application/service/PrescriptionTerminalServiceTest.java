package com.mediflow.billing.application.service;

import com.mediflow.billing.application.port.out.PrescriptionTerminalRepositoryPort;
import com.mediflow.billing.infrastructure.messaging.PrescriptionChargeDecoder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrescriptionTerminalServiceTest {
    private final PrescriptionTerminalRepositoryPort repository = mock(PrescriptionTerminalRepositoryPort.class);
    private com.mediflow.billing.application.dto.command.PrescriptionTerminalCommand command() throws Exception {
        return new PrescriptionChargeDecoder(new ObjectMapper().findAndRegisterModules()).decodeTerminal("prescription.expired",
                Files.readAllBytes(Path.of("../pharmacy-service/src/test/resources/contracts/care-finance-v1/prescription.expired.v1.json")));
    }
    @Test void apply_future_deniesBeforeRepositoryOrClockDependentMoneyEffect() throws Exception {
        var c = command(); var service = new PrescriptionTerminalService(repository, Clock.fixed(c.snapshot().createdAt().minusSeconds(6), ZoneOffset.UTC));
        assertThatThrownBy(() -> service.apply(c)).hasMessage("BILLING_PRESCRIPTION_TERMINAL_FUTURE"); verifyNoInteractions(repository);
    }
    @Test void apply_exactSkewBoundary_passesImmutableFactToPort() throws Exception {
        var c = command(); new PrescriptionTerminalService(repository, Clock.fixed(c.snapshot().createdAt().minusSeconds(5), ZoneOffset.UTC)).apply(c);
        verify(repository).receive(c); verifyNoMoreInteractions(repository);
    }
    @Test void apply_repositoryFailure_doesNotFabricateSuccessfulRefund() throws Exception {
        var c = command(); doThrow(new IllegalStateException("retry")).when(repository).receive(c);
        assertThatThrownBy(() -> new PrescriptionTerminalService(repository, Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC)).apply(c))
                .hasMessage("retry");
    }
}
