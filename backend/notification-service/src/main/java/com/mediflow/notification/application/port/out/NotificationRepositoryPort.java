package com.mediflow.notification.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.mediflow.notification.domain.model.Notification;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;

/**
 * Out-port — "tôi cần ai đó biết cách lưu và tìm bản ghi thông báo".
 * {@code NotificationPersistenceAdapter} (infrastructure, Phần 4/5) hiện thực. Phân trang dùng
 * {@link PageQuery}/{@link PageResult} của common — không dùng Spring Data {@code Pageable} ở
 * tầng application. Chữ ký bám sát backend-spec/07-notification.md §5.
 */
public interface NotificationRepositoryPort {

    /** Lưu mới hoặc cập nhật. Phải lưu được cả bản ghi {@code PENDING} lẫn {@code FAILED} (BR-N3/BR-N4). */
    Notification save(Notification n);

    /** Tìm một thông báo. Không có → {@link Optional#empty()} → application ném {@code NotificationNotFoundException}. */
    Optional<Notification> findById(UUID id);

    /** Danh sách thông báo của một bệnh nhân, sắp xếp mới nhất trước, có phân trang. */
    PageResult<Notification> findByPatient(UUID patientId, PageQuery page);
}
