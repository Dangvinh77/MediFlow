package com.mediflow.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import com.mediflow.notification.application.dto.response.NotificationDTO;
import com.mediflow.notification.application.event.NotificationSentEvent;
import com.mediflow.notification.application.mapper.NotificationDtoMapper;
import com.mediflow.notification.application.port.in.NotificationTrigger;
import com.mediflow.notification.application.port.out.NotificationEventPublisherPort;
import com.mediflow.notification.application.port.out.NotificationRepositoryPort;
import com.mediflow.notification.application.port.out.NotificationSenderPort;
import com.mediflow.notification.application.port.out.ProcessedEventPort;
import com.mediflow.notification.domain.exception.NotificationAccessDeniedException;
import com.mediflow.notification.domain.model.Notification;
import com.mediflow.notification.domain.model.NotificationChannel;
import com.mediflow.notification.domain.model.NotificationStatus;

/** Luồng gửi/đọc — BR-N3, BR-N4, BR-N5, BR-N6, BR-N8, BR-N9 (tầng application). */
class NotificationApplicationServiceTest {

    private final NotificationRepositoryPort repo = mock(NotificationRepositoryPort.class);
    private final ProcessedEventPort processedEvent = mock(ProcessedEventPort.class);
    private final NotificationEventPublisherPort publisher = mock(NotificationEventPublisherPort.class);
    private final NotificationDtoMapper mapper = mock(NotificationDtoMapper.class);
    private final NotificationSenderPort emailSender = mock(NotificationSenderPort.class);
    private final NotificationSenderPort smsSender = mock(NotificationSenderPort.class);
    private final NotificationSenderPort inAppSender = mock(NotificationSenderPort.class);

    private final NotificationApplicationService service;

    /** {@code Notification} là đối tượng mutable — chụp trạng thái NGAY lúc save để assert không bị
     *  lệch vì lần mutate sau (markSent/markFailed). */
    private final List<NotificationStatus> savedStatusSnapshots = new java.util.ArrayList<>();

    NotificationApplicationServiceTest() {
        when(emailSender.channel()).thenReturn(NotificationChannel.EMAIL);
        when(smsSender.channel()).thenReturn(NotificationChannel.SMS);
        when(inAppSender.channel()).thenReturn(NotificationChannel.IN_APP);
        when(emailSender.send(any())).thenReturn(Optional.empty());
        when(smsSender.send(any())).thenReturn(Optional.empty());
        when(inAppSender.send(any())).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> {
            Notification arg = i.getArgument(0);
            savedStatusSnapshots.add(arg.getStatus());
            return arg;
        });
        when(mapper.toDto(any())).thenReturn(mock(NotificationDTO.class));
        service = new NotificationApplicationService(repo, processedEvent, publisher, mapper,
                List.of(emailSender, smsSender, inAppSender));
    }

    private static NotificationTrigger trigger(String email, String phone) {
        return new NotificationTrigger(UUID.randomUUID(), "payment.completed", UUID.randomUUID(),
                "Thanh toán thành công", "Hóa đơn HD1 đã được thanh toán.", email, phone);
    }

    // ---- BR-N3 : lưu PENDING trước khi gửi ----
    @Test
    void handleEvent_persistsPendingBeforeSend() {
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);

        service.handleEvent(trigger("benhnhan@example.com", null));

        // save đầu tiên phải là bản ghi PENDING (chụp lúc save, trước khi markSent mutate)
        assertThat(savedStatusSnapshots.get(0)).isEqualTo(NotificationStatus.PENDING);
        InOrder order = inOrder(repo, emailSender);
        order.verify(repo).save(any());
        order.verify(emailSender).send(any());
    }

    // ---- BR-N4 : gửi thất bại → FAILED + lý do, KHÔNG ném lỗi ----
    @Test
    void handleEvent_senderFails_marksFailedNoThrow() {
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);
        when(emailSender.send(any())).thenReturn(Optional.of("SMTP timeout"));

        assertThatCode(() -> service.handleEvent(trigger("benhnhan@example.com", null)))
                .doesNotThrowAnyException();

        ArgumentCaptor<Notification> saves = ArgumentCaptor.forClass(Notification.class);
        verify(repo, times(2)).save(saves.capture());
        Notification last = saves.getAllValues().get(saves.getAllValues().size() - 1);
        assertThat(last.getStatus()).isEqualTo(NotificationStatus.FAILED);   // FAILED là trạng thái kết thúc, an toàn để assert sau
        assertThat(last.getFailureReason()).isEqualTo("SMTP timeout");
        assertThat(savedStatusSnapshots).containsExactly(NotificationStatus.PENDING, NotificationStatus.FAILED);
    }

    // ---- BR-N5 : consumer idempotent ----
    @Test
    void handleEvent_sameEventTwice_createsOneNotification() {
        NotificationTrigger t = trigger("benhnhan@example.com", null);
        when(processedEvent.alreadyProcessed(t.eventId())).thenReturn(false, true);

        service.handleEvent(t);
        service.handleEvent(t);   // gửi lại

        verify(processedEvent, times(1)).markProcessed(any(), any());
        verify(publisher, times(1)).publishSent(any());
    }

    // ---- BR-N6 : PATIENT không đọc được thông báo của người khác ----
    @Test
    void getById_otherPatient_throwsAccessDenied() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        Notification n = Notification.restore(id, owner, "T", "C", NotificationChannel.IN_APP, null,
                NotificationStatus.SENT, null, 1, Instant.now(), Instant.now());
        when(repo.findById(id)).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.getById(id, other, false))
                .isInstanceOf(NotificationAccessDeniedException.class);

        // nhân viên xem được của mọi bệnh nhân
        assertThatCode(() -> service.getById(id, other, true)).doesNotThrowAnyException();
    }

    @Test
    void byPatient_otherPatientIsDeniedBeforeQuery() {
        assertThatThrownBy(() -> service.byPatient(UUID.randomUUID(), null, UUID.randomUUID(), false))
                .isInstanceOf(NotificationAccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(repo);
    }

    @Test
    void byPatient_missingIdentityIsDeniedBeforeQuery() {
        assertThatThrownBy(() -> service.byPatient(UUID.randomUUID(), null, null, false))
                .isInstanceOf(NotificationAccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(repo);
    }

    @Test
    void byPatient_ownerAndStaffCanRead() {
        UUID patient = UUID.randomUUID();
        var page = com.mediflow.common.api.PageQuery.of(0, 20);
        when(repo.findByPatient(patient, page)).thenReturn(com.mediflow.common.api.PageResult.empty(page));
        assertThat(service.byPatient(patient, page, patient, false).content()).isEmpty();
        assertThat(service.byPatient(patient, page, null, true).content()).isEmpty();
        verify(repo, times(2)).findByPatient(patient, page);
    }

    // ---- BR-N8 : notification.sent publish kèm trạng thái cuối ----
    @Test
    void handleEvent_publishesSentWithStatus() {
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);

        service.handleEvent(trigger("benhnhan@example.com", null));

        ArgumentCaptor<NotificationSentEvent> ev = ArgumentCaptor.forClass(NotificationSentEvent.class);
        verify(publisher).publishSent(ev.capture());
        assertThat(ev.getValue().status()).isEqualTo(NotificationStatus.SENT);
        assertThat(ev.getValue().channel()).isEqualTo(NotificationChannel.EMAIL);
    }

    // ---- BR-N9 : chọn kênh theo thứ tự ưu tiên EMAIL → SMS → IN_APP ----
    @Test
    void handleEvent_choosesChannelByPriorityEmailSmsThenInApp() {
        when(processedEvent.alreadyProcessed(any())).thenReturn(false);

        // có email hợp lệ + sđt hợp lệ → EMAIL
        service.handleEvent(trigger("benhnhan@example.com", "0912345678"));
        assertThat(firstSavedChannel()).isEqualTo(NotificationChannel.EMAIL);
        org.mockito.Mockito.reset(repo);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        // email không hợp lệ, chỉ có sđt hợp lệ → SMS
        service.handleEvent(trigger("khong-phai-email", "0912345678"));
        assertThat(firstSavedChannel()).isEqualTo(NotificationChannel.SMS);
        org.mockito.Mockito.reset(repo);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        // không có email lẫn sđt hợp lệ → IN_APP (không cần địa chỉ)
        service.handleEvent(trigger(null, null));
        Notification n = firstSaved();
        assertThat(n.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(n.getRecipientAddress()).isNull();
    }

    private Notification firstSaved() {
        ArgumentCaptor<Notification> c = ArgumentCaptor.forClass(Notification.class);
        verify(repo, org.mockito.Mockito.atLeastOnce()).save(c.capture());
        return c.getAllValues().get(0);
    }

    private NotificationChannel firstSavedChannel() {
        return firstSaved().getChannel();
    }
}
