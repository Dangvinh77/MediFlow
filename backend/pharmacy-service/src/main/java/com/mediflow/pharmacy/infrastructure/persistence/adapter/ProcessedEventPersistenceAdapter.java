package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.pharmacy.application.port.out.ProcessedEventPort;
import com.mediflow.pharmacy.infrastructure.persistence.jpaEntity.ProcessedEventJpaEntity;
import com.mediflow.pharmacy.infrastructure.persistence.repository.ProcessedEventJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link ProcessedEventPort} — sổ chống xử lý trùng (BR-D9). Khi RabbitMQ gửi
 * lại cùng một event, bảng PROCESSED_EVENT cho biết đã xử lý rồi để consumer bỏ qua.
 *
 * <p>{@code markProcessed} được gọi trong CÙNG transaction với nghiệp vụ (onPaymentCompleted),
 * nên nếu nghiệp vụ rollback thì dấu "đã xử lý" cũng rollback theo — đúng ý muốn: chỉ đánh dấu
 * xử lý khi cả chuỗi thành công.
 */
@Component
@RequiredArgsConstructor
public class ProcessedEventPersistenceAdapter implements ProcessedEventPort {

    private final ProcessedEventJpaRepository jpaRepo;

    @Override
    public boolean alreadyProcessed(UUID eventId) {
        return jpaRepo.existsById(eventId);
    }

    @Override
    public void markProcessed(UUID eventId, String routingKey) {
        jpaRepo.save(ProcessedEventJpaEntity.builder()
                .eventId(eventId)
                .routingKey(routingKey)
                .build());
    }

    @SuppressWarnings("unused") // giữ chân khả năng đọc lại nếu sau này cần trace — xoá được
    private Optional<ProcessedEventJpaEntity> find(UUID eventId) {
        return jpaRepo.findById(eventId);
    }
}
