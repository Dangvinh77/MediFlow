package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.AdmissionDepositRequestCommand;
import com.mediflow.billing.application.port.out.AdmissionDepositRequestRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdmissionDepositRequestServiceTest {
    private final AdmissionDepositRequestRepositoryPort repository = mock(AdmissionDepositRequestRepositoryPort.class);
    private final AdmissionDepositRequestService service = new AdmissionDepositRequestService(repository);

    private AdmissionDepositRequestCommand command;

    @BeforeEach
    void setUp() {
        UUID admissionId = UUID.randomUUID();
        command = new AdmissionDepositRequestCommand(UUID.randomUUID(), fingerprint(), fingerprint(), "corr-1",
                admissionId, UUID.randomUUID(), UUID.randomUUID(), "ADMISSION", admissionId, "INPATIENT_DEPOSIT",
                new BigDecimal("150000.00"), "Initial admission deposit", Instant.parse("2026-09-28T02:05:00Z"));
    }

    @Test
    void issue_newSource_opensAccountAndSavesRequest() {
        when(repository.lockRecordedSource(command)).thenReturn(Optional.empty());
        UUID account = UUID.randomUUID();
        when(repository.openAndLockExactAccount(command)).thenReturn(account);
        UUID requestId = UUID.randomUUID();
        when(repository.saveRequest(command, account)).thenReturn(requestId);

        UUID result = service.issue(command);

        assertThat(result).isEqualTo(requestId);
        verify(repository).claimDelivery(command);
        verify(repository).saveRequest(command, account);
    }

    @Test
    void issue_exactReplay_returnsRecordedRequestWithoutReamounting() {
        UUID recordedRequest = UUID.randomUUID();
        when(repository.lockRecordedSource(command)).thenReturn(Optional.of(recordedRequest));

        UUID result = service.issue(command);

        assertThat(result).isEqualTo(recordedRequest);
        verify(repository, never()).openAndLockExactAccount(any());
        verify(repository, never()).saveRequest(any(), any());
    }

    private static String fingerprint() {
        return "b".repeat(64);
    }
}
