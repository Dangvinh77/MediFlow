package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.pharmacy.application.port.out.ProcessedEventPort;
import com.mediflow.pharmacy.infrastructure.persistence.repository.ProcessedEventJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link ProcessedEventPort} — sổ chống xử lý trùng (BR-D9). Khi RabbitMQ gửi
 * lại cùng một event, bảng PROCESSED_EVENT cho biết đã xử lý rồi để consumer bỏ qua.
 *
 * <p>{@code claimIfAbsent} được gọi sau khi nghiệp vụ đạt trạng thái terminal. Claim chạy trong
 * transaction riêng và dùng unique key ở database, nên lỗi hạ tầng tạm thời không làm mất event
 * trước khi RabbitMQ retry.
 */
@Component
@RequiredArgsConstructor
public class ProcessedEventPersistenceAdapter implements ProcessedEventPort {

    private final ProcessedEventJpaRepository jpaRepo;

    @Override
    public boolean alreadyProcessed(UUID eventId) {
        return jpaRepo.existsById(eventId);
    }

    /**
     * Claim idempotency key bằng một INSERT ... ON CONFLICT duy nhất.
     *
     * @param eventId mã event cần claim
     * @param routingKey routing key nguồn
     * @return true khi row mới được tạo, false khi event đã được claim
     */
    @Override
    @Transactional
    public boolean claimIfAbsent(UUID eventId, String routingKey) {
        return jpaRepo.insertIfAbsent(eventId, routingKey) == 1;
    }
}
