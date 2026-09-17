package com.mediflow.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mediflow.report.infrastructure.persistence.repository.ProcessedEventJpaRepository;

@ExtendWith(MockitoExtension.class)
class ProcessedEventPersistenceAdapterTest {

    @Mock private ProcessedEventJpaRepository repository;

    @Test
    void claimIfAbsent_insertedRow_returnsTrue() {
        UUID eventId = UUID.randomUUID();
        when(repository.insertIfAbsent(eventId, "medicalrecord.created")).thenReturn(1);
        ProcessedEventPersistenceAdapter adapter = new ProcessedEventPersistenceAdapter(repository);

        assertThat(adapter.claimIfAbsent(eventId, "medicalrecord.created")).isTrue();
        verify(repository).insertIfAbsent(eventId, "medicalrecord.created");
    }

    @Test
    void claimIfAbsent_conflict_returnsFalse() {
        UUID eventId = UUID.randomUUID();
        when(repository.insertIfAbsent(eventId, "medicalrecord.created")).thenReturn(0);
        ProcessedEventPersistenceAdapter adapter = new ProcessedEventPersistenceAdapter(repository);

        assertThat(adapter.claimIfAbsent(eventId, "medicalrecord.created")).isFalse();
    }
}
