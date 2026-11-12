package com.mediflow.lab.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.common.exception.ForbiddenOperationException;
import com.mediflow.lab.application.port.out.CurrentReaderPort;
import com.mediflow.lab.application.port.out.LabCriticalNoticeRepositoryPort;
import com.mediflow.lab.application.port.out.ReaderIdentity;
import com.mediflow.lab.domain.model.LabCriticalNotice;
import com.mediflow.lab.domain.model.LabFlag;

/** The doctors of the requesting department acknowledge; anyone else is refused. */
class LabCriticalNoticeServiceTest {
    static final Instant AT = Instant.parse("2026-11-11T06:00:00Z");
    final LabCriticalNoticeRepositoryPort notices = mock(LabCriticalNoticeRepositoryPort.class);
    final CurrentReaderPort readers = mock(CurrentReaderPort.class);
    final UUID department = UUID.randomUUID();
    final LabCriticalNoticeService service = new LabCriticalNoticeService(notices, readers,
            Clock.fixed(AT, ZoneOffset.UTC), true);
    final LabCriticalNotice notice = LabCriticalNotice.raise(UUID.randomUUID(), 1, "K", "Kali", "7.2", "mmol/L",
            LabFlag.CRITICAL_HIGH, UUID.randomUUID(), department, AT.minusSeconds(60));

    void doctor(UUID staff, UUID dept) {
        when(readers.current()).thenReturn(Optional.of(new ReaderIdentity(UUID.randomUUID(), staff, "DOCTOR", dept)));
    }

    @Test void doctorOfAnotherDepartmentIsForbidden() {
        doctor(UUID.randomUUID(), UUID.randomUUID());
        when(notices.findForUpdate(notice.getNoticeId())).thenReturn(Optional.of(notice));
        assertThatThrownBy(() -> service.acknowledge(notice.getTestId(), notice.getNoticeId()))
                .isInstanceOfSatisfying(ForbiddenOperationException.class,
                        e -> assertThat(e.getCode()).isEqualTo("LAB_CRITICAL_NOT_RESPONSIBLE"));
        verify(notices, never()).update(any());
    }

    @Test void acknowledgingTwiceReturnsTheSameAnswer() {
        UUID doctor = UUID.randomUUID();
        doctor(doctor, department);
        when(notices.findForUpdate(notice.getNoticeId())).thenReturn(Optional.of(notice));
        var first = service.acknowledge(notice.getTestId(), notice.getNoticeId());
        var second = service.acknowledge(notice.getTestId(), notice.getNoticeId());
        assertThat(first).isEqualTo(second);
        assertThat(first.status()).isEqualTo("ACKNOWLEDGED");
        assertThat(first.acknowledgedBy()).isEqualTo(doctor);
        verify(notices, times(1)).update(notice);
    }

    @Test void noticeOfAnotherTestIsNotFound() {
        doctor(UUID.randomUUID(), department);
        when(notices.findForUpdate(notice.getNoticeId())).thenReturn(Optional.of(notice));
        assertThatThrownBy(() -> service.acknowledge(UUID.randomUUID(), notice.getNoticeId()))
                .isInstanceOf(com.mediflow.common.exception.ResourceNotFoundException.class);
    }

    @Test void doctorsListTheirDepartmentOnly() {
        doctor(UUID.randomUUID(), department);
        when(notices.find("OPEN", department)).thenReturn(List.of(notice));
        assertThat(service.list("OPEN")).singleElement().satisfies(n -> assertThat(n.flag()).isEqualTo("CRITICAL_HIGH"));
    }
}
