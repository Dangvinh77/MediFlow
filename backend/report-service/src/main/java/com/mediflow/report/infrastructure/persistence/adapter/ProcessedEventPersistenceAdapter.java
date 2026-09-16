package com.mediflow.report.infrastructure.persistence.adapter;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.port.out.ProcessedEventPort;
import com.mediflow.report.infrastructure.persistence.repository.ProcessedEventJpaRepository;

import lombok.RequiredArgsConstructor;

/** Driven adapter implementing atomic event claims with PostgreSQL ON CONFLICT. */
@Component
@RequiredArgsConstructor
public class ProcessedEventPersistenceAdapter implements ProcessedEventPort {

    private final ProcessedEventJpaRepository repository;

    @Override
    @Transactional
    public boolean claimIfAbsent(UUID eventId, String routingKey) {
        return repository.insertIfAbsent(eventId, routingKey) == 1;
    }
}
