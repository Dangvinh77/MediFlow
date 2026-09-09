package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.ExternalResultRepositoryPort;
import com.mediflow.clinical.domain.model.ExternalResultType;
import com.mediflow.clinical.infrastructure.persistence.repository.AttachedResultJpaRepository;

@Component
public class ExternalResultPersistenceAdapter implements ExternalResultRepositoryPort {

    private final AttachedResultJpaRepository repository;

    public ExternalResultPersistenceAdapter(AttachedResultJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void attach(UUID recordId, ExternalResultType type, UUID referenceId, String summary) {
        repository.insertIfAbsent(recordId, type.name(), referenceId, summary);
    }
}
