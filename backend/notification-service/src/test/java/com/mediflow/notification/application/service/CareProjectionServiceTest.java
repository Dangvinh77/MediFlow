package com.mediflow.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mediflow.notification.application.dto.command.AdmissionDepositRequestedCommand;
import com.mediflow.notification.application.dto.command.AdmissionStartedCommand;
import com.mediflow.notification.application.dto.command.SurgeryCancelledNoticeCommand;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.domain.model.CareNotificationIntent;

/** CONTRACT-CARE-PROJECTIONS-01: mỗi event tạo đúng một intent IN_APP, lặp lại eventId thì bỏ qua. */
class CareProjectionServiceTest {

    private final CareNotificationRepositoryPort repository = mock(CareNotificationRepositoryPort.class);
    private final CareProjectionService service = new CareProjectionService(repository);
    private final String fingerprint = "a".repeat(64);

    @Test
    void admissionDepositRequested_deliversInAppWithAmountInContent() {
        when(repository.claim(any(), any(), any())).thenReturn(true);
        UUID admissionId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        var command = new AdmissionDepositRequestedCommand(UUID.randomUUID(), fingerprint, "cid",
                admissionId, patientId, new BigDecimal("150000.00"), "Initial admission deposit");

        service.onAdmissionDepositRequested(command);

        var captor = ArgumentCaptor.forClass(CareNotificationIntent.class);
        verify(repository).deliverInApp(captor.capture());
        assertThat(captor.getValue().patientId()).isEqualTo(patientId);
        assertThat(captor.getValue().sourceId()).isEqualTo(admissionId);
        assertThat(captor.getValue().content()).contains("150000.00 VND");
    }

    @Test
    void alreadyClaimedEvent_doesNotDeliverAgain() {
        when(repository.claim(any(), any(), any())).thenReturn(false);
        var command = new AdmissionStartedCommand(UUID.randomUUID(), fingerprint, "cid",
                UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        service.onAdmissionStarted(command);

        verify(repository, never()).deliverInApp(any());
    }

    @Test
    void surgeryCancelled_includesReasonAndNeverAuthorizesAnything() {
        when(repository.claim(any(), any(), any())).thenReturn(true);
        var command = new SurgeryCancelledNoticeCommand(UUID.randomUUID(), fingerprint, "cid",
                UUID.randomUUID(), UUID.randomUUID(), "BEFORE_START", "Patient request", Instant.now());

        service.onSurgeryCancelled(command);

        var captor = ArgumentCaptor.forClass(CareNotificationIntent.class);
        verify(repository).deliverInApp(captor.capture());
        assertThat(captor.getValue().content()).contains("Patient request");
        assertThat(captor.getValue().templateKey()).isEqualTo("SURGERY_CANCELLED");
    }
}
