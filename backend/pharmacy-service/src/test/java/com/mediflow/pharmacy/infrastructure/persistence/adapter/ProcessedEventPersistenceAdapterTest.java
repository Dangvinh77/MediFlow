package com.mediflow.pharmacy.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mediflow.pharmacy.infrastructure.persistence.repository.ProcessedEventJpaRepository;

/** Kiểm tra adapter chuyển kết quả atomic claim từ persistence port đúng nghĩa. */
class ProcessedEventPersistenceAdapterTest {

    private final ProcessedEventJpaRepository repository = mock(ProcessedEventJpaRepository.class);
    private final ProcessedEventPersistenceAdapter adapter = new ProcessedEventPersistenceAdapter(repository);

    /** INSERT mới trả true để consumer được quyền xử lý event. */
    @Test
    void claimIfAbsent_inserted_returnsTrue() {
        UUID eventId = UUID.randomUUID();
        when(repository.insertIfAbsent(eventId, "payment.completed")).thenReturn(1);

        assertThat(adapter.claimIfAbsent(eventId, "payment.completed")).isTrue();
    }

    /** ON CONFLICT trả zero thì event đã có owner và phải bị bỏ qua. */
    @Test
    void claimIfAbsent_conflict_returnsFalse() {
        UUID eventId = UUID.randomUUID();
        when(repository.insertIfAbsent(eventId, "payment.completed")).thenReturn(0);

        assertThat(adapter.claimIfAbsent(eventId, "payment.completed")).isFalse();
    }
}
