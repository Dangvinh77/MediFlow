package com.mediflow.lab.infrastructure.persistence.adapter;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.lab.application.port.out.ProcessedEventPort;
import com.mediflow.lab.infrastructure.persistence.repository.ProcessedEventJpaRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProcessedEventPersistenceAdapter implements ProcessedEventPort {
    private final ProcessedEventJpaRepository repository;

    @Override public boolean tryClaim(UUID eventId, String eventType) {
        return repository.insertIfAbsent(eventId, eventType) == 1;
    }
}
