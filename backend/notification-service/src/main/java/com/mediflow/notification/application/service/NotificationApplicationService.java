package com.mediflow.notification.application.service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.notification.application.dto.request.SendNotificationRequest;
import com.mediflow.notification.application.dto.response.NotificationDTO;
import com.mediflow.notification.application.event.NotificationSentEvent;
import com.mediflow.notification.application.mapper.NotificationDtoMapper;
import com.mediflow.notification.application.port.in.NotificationTrigger;
import com.mediflow.notification.application.port.in.ReadNotificationUseCase;
import com.mediflow.notification.application.port.in.SendNotificationUseCase;
import com.mediflow.notification.application.port.out.NotificationEventPublisherPort;
import com.mediflow.notification.application.port.out.NotificationRepositoryPort;
import com.mediflow.notification.application.port.out.NotificationSenderPort;
import com.mediflow.notification.application.port.out.ProcessedEventPort;
import com.mediflow.notification.domain.exception.NotificationAccessDeniedException;
import com.mediflow.notification.domain.exception.NotificationNotFoundException;
import com.mediflow.notification.domain.model.Notification;
import com.mediflow.notification.domain.model.NotificationChannel;

/**
 * Application service của notification — hiện thực {@link SendNotificationUseCase} và
 * {@link ReadNotificationUseCase} theo luồng 7 bước ở backend-spec/07-notification.md §7.
 *
 * <p>Điều phối: lưu {@code PENDING} trước (BR-N3) → gửi qua đúng {@link NotificationSenderPort}
 * (BR-N4, bộ gửi không ném lỗi khi thất bại) → ghi trạng thái cuối → publish
 * {@code notification.sent} kèm trạng thái đó (BR-N8). Không import JPA/AMQP/HTTP.
 */
@Service
public class NotificationApplicationService implements SendNotificationUseCase, ReadNotificationUseCase {

    private final NotificationRepositoryPort repo;
    private final ProcessedEventPort processedEvent;
    private final NotificationEventPublisherPort eventPublisher;
    private final NotificationDtoMapper mapper;
    private final Map<NotificationChannel, NotificationSenderPort> senders;

    public NotificationApplicationService(NotificationRepositoryPort repo, ProcessedEventPort processedEvent,
                                          NotificationEventPublisherPort eventPublisher, NotificationDtoMapper mapper,
                                          List<NotificationSenderPort> senderList) {
        this.repo = repo;
        this.processedEvent = processedEvent;
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
        this.senders = new EnumMap<>(NotificationChannel.class);
        for (NotificationSenderPort sender : senderList) {
            this.senders.put(sender.channel(), sender);
        }
    }

    // ============================================================
    // SendNotificationUseCase
    // ============================================================

    /** Gửi theo yêu cầu API. Địa chỉ không hợp lệ theo kênh → {@code Notification.create} ném 422 (BR-N1/N2). */
    @Override
    @Transactional
    public NotificationDTO send(SendNotificationRequest r) {
        Notification n = Notification.create(r.patientId(), r.title(), r.content(), r.channel(), r.recipientAddress());
        Notification pending = repo.save(n);
        return sendRecordAndPublish(pending);
    }

    /**
     * Xử lý thông báo bắt nguồn từ event. Idempotent theo {@code eventId} (BR-N5); chọn kênh theo
     * thứ tự ưu tiên EMAIL → SMS → IN_APP (BR-N9). Đánh dấu event đã xử lý nằm cùng transaction
     * với bước lưu {@code PENDING} (§7 bước 6).
     */
    @Override
    @Transactional
    public void handleEvent(NotificationTrigger trigger) {
        if (processedEvent.alreadyProcessed(trigger.eventId())) {
            return;
        }
        NotificationChannel channel = chooseChannel(trigger.email(), trigger.phone());
        String address = addressFor(channel, trigger);

        Notification n = Notification.create(trigger.patientId(), trigger.title(), trigger.content(), channel, address);
        Notification pending = repo.save(n);
        processedEvent.markProcessed(trigger.eventId(), trigger.routingKey());

        sendRecordAndPublish(pending);
    }

    // ============================================================
    // ReadNotificationUseCase
    // ============================================================

    /** BR-N6: {@code PATIENT} chỉ đọc được thông báo của chính mình; nhân viên đọc được của mọi bệnh nhân. */
    @Override
    @Transactional(readOnly = true)
    public NotificationDTO getById(UUID id, UUID callerPatientId, boolean isStaff) {
        Notification n = repo.findById(id)
                .orElseThrow(() -> new NotificationNotFoundException("Không tìm thấy thông báo id=" + id));
        if (!isStaff && !n.getPatientId().equals(callerPatientId)) {
            throw new NotificationAccessDeniedException("Không có quyền xem thông báo này");
        }
        return mapper.toDto(n);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<NotificationDTO> byPatient(UUID patientId, PageQuery page) {
        return repo.findByPatient(patientId, page).map(mapper::toDto);
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** BR-N9 — EMAIL nếu email hợp lệ, không thì SMS nếu số điện thoại hợp lệ, không nữa thì IN_APP. */
    private static NotificationChannel chooseChannel(String email, String phone) {
        if (Notification.isValidEmail(email)) {
            return NotificationChannel.EMAIL;
        }
        if (Notification.isValidPhone(phone)) {
            return NotificationChannel.SMS;
        }
        return NotificationChannel.IN_APP;
    }

    private static String addressFor(NotificationChannel channel, NotificationTrigger t) {
        return switch (channel) {
            case EMAIL -> t.email();
            case SMS -> t.phone();
            case IN_APP -> null;
        };
    }

    private NotificationDTO sendRecordAndPublish(Notification pending) {
        NotificationSenderPort sender = senders.get(pending.getChannel());
        if (sender == null) {
            // Không có bộ gửi cho kênh này (cấu hình thiếu) — ghi FAILED, không ném (BR-N4).
            pending.markFailed("Không có bộ gửi cho kênh " + pending.getChannel());
        } else {
            Optional<String> failure = sender.send(pending);
            if (failure.isEmpty()) {
                pending.markSent(Instant.now());
            } else {
                pending.markFailed(failure.get());
            }
        }
        Notification done = repo.save(pending);

        eventPublisher.publishSent(new NotificationSentEvent(
                UUID.randomUUID(), Instant.now(), null,
                done.getNotificationId(), done.getPatientId(), done.getChannel(), done.getStatus()));

        return mapper.toDto(done);
    }
}
