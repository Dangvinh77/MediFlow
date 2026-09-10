package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.ProcessedEventPort;
import com.mediflow.clinical.infrastructure.persistence.jpaEntity.ProcessedEventJpaEntity;
import com.mediflow.clinical.infrastructure.persistence.repository.ProcessedEventJpaRepository;

@Component
public class ProcessedEventPersistenceAdapter implements ProcessedEventPort {

    private final ProcessedEventJpaRepository repository;

    public ProcessedEventPersistenceAdapter(ProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean alreadyProcessed(UUID eventId) {
        return repository.existsById(eventId);
    }

    @Override
    public void markProcessed(UUID eventId, String routingKey) {
        repository.save(ProcessedEventJpaEntity.builder()
                .eventId(eventId)
                .routingKey(routingKey)
                .build());
    }
}
