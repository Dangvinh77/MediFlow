package com.mediflow.billing.infrastructure.persistence.adapter;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.billing.application.port.out.ProcessedEventPort;
import com.mediflow.billing.infrastructure.persistence.jpaEntity.ProcessedEventJpaEntity;
import com.mediflow.billing.infrastructure.persistence.repository.ProcessedEventJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link ProcessedEventPort} — sổ chống xử lý trùng (BR-B6/BR-B7). {@code markProcessed}
 * được gọi trong CÙNG transaction với nghiệp vụ của consumer, nên nếu nghiệp vụ rollback thì dấu
 * "đã xử lý" cũng rollback theo — chỉ đánh dấu khi cả chuỗi thành công.
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
}
