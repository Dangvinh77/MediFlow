package com.mediflow.notification.infrastructure.persistence.adapter;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.notification.application.port.out.ProcessedEventPort;
import com.mediflow.notification.infrastructure.persistence.repository.ProcessedEventJpaRepository;

import lombok.RequiredArgsConstructor;

/**
 * Adapter cho {@link ProcessedEventPort} — sổ chống xử lý trùng (BR-N5). {@code markProcessed} nằm
 * cùng transaction với bước lưu {@code PENDING} (§7 bước 6): nghiệp vụ rollback thì dấu "đã xử lý"
 * cũng rollback theo.
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
        jpaRepo.insertEvent(eventId, routingKey);
    }
}
