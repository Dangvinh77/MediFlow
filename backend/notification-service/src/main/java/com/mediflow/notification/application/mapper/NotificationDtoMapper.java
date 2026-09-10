package com.mediflow.notification.application.mapper;

import org.mapstruct.Mapper;

import com.mediflow.notification.application.dto.response.NotificationDTO;
import com.mediflow.notification.domain.model.Notification;

/**
 * MapStruct mapper: {@link Notification} (domain) → {@link NotificationDTO} (response).
 *
 * <p>Mọi field của DTO đều trùng tên với getter của domain nên không cần {@code @Mapping} thủ
 * công. Hai field {@code recipientAddress} và {@code retryCount} của domain <b>không</b> có
 * trong DTO (PII / nội bộ, §6) → MapStruct tự bỏ qua, dữ liệu nhạy cảm không lọt ra ngoài.
 * Mapper chỉ đổi hình dạng dữ liệu, không mang quy tắc nghiệp vụ.
 */
@Mapper(componentModel = "spring")
public interface NotificationDtoMapper {

    /** Domain → DTO (trả về client). */
    NotificationDTO toDto(Notification notification);
}
