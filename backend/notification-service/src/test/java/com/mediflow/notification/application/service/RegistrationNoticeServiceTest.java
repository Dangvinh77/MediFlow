package com.mediflow.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.notification.application.dto.command.RegistrationNoticeCommand;
import com.mediflow.notification.application.port.out.CareNotificationRepositoryPort;
import com.mediflow.notification.application.port.out.RegistrationNoticeProjectionPort;
import com.mediflow.notification.domain.model.CareNotificationIntent;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegistrationNoticeServiceTest {
    private final CareNotificationRepositoryPort history = mock(CareNotificationRepositoryPort.class);
    private final RegistrationNoticeProjectionPort projections = mock(RegistrationNoticeProjectionPort.class);
    private final RegistrationNoticeService service = new RegistrationNoticeService(history, projections);
    private final UUID patient = UUID.randomUUID();
    private final UUID registration = UUID.randomUUID();

    @BeforeEach void accept() {
        when(history.claim(any(), eq(RegistrationNoticeCommand.EVENT_TYPE), anyString())).thenReturn(true, false);
        when(projections.advance(any())).thenReturn(true);
    }

    private RegistrationNoticeCommand command(boolean rescheduled) {
        return new RegistrationNoticeCommand(UUID.randomUUID(), "raw", "snapshot", "trace", registration, 3, patient, "CONFIRMED", rescheduled);
    }

    private CareNotificationIntent delivered() {
        var captor = ArgumentCaptor.forClass(CareNotificationIntent.class);
        verify(history).deliverInApp(captor.capture());
        return captor.getValue();
    }

    @Test void rescheduledNoticeGoesToThePatientOnce() {
        var command = command(true);
        service.receive(command);
        service.receive(command);
        verify(history, times(1)).deliverInApp(any());
        var notice = delivered();
        assertThat(notice.patientId()).isEqualTo(patient);
        assertThat(notice.title()).isEqualTo("Lịch khám đã thay đổi");
        assertThat(notice.templateKey()).isEqualTo("OUTPATIENT_REGISTRATION_RESCHEDULED");
        assertThat(notice.sourceId()).isEqualTo(registration);
    }

    @Test void rescheduledContentAsksToCheckNewDetailsWithoutReason() {
        service.receive(command(true));
        assertThat(delivered().content()).isEqualTo("Lịch khám của bạn đã được đổi. Vui lòng xem ngày, giờ, bác sĩ và phòng mới trong phiếu khám.");
    }

    @Test void confirmedNoticeUnchangedWithoutReschedule() {
        service.receive(command(false));
        var notice = delivered();
        assertThat(notice.title()).isEqualTo("Phiếu khám đã xác nhận");
        assertThat(notice.templateKey()).isEqualTo("OUTPATIENT_REGISTRATION_CONFIRMED");
    }
}
