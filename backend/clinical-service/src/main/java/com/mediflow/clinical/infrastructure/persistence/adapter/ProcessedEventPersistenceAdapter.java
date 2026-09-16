package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.ProcessedEventPort;
import com.mediflow.clinical.infrastructure.persistence.repository.ProcessedEventJpaRepository;

@Component
public class ProcessedEventPersistenceAdapter implements ProcessedEventPort {

    private final ProcessedEventJpaRepository repository;

    public ProcessedEventPersistenceAdapter(ProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean tryClaim(UUID eventId, String eventType) {
        return repository.insertIfAbsent(eventId, eventType) == 1;
    }
}
