package com.mediflow.notification.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Slice test cho {@link ProcessedEventPersistenceAdapter} — sổ chống xử lý trùng (BR-N5). */
@DataJpaTest
@Import(ProcessedEventPersistenceAdapter.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ProcessedEventPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProcessedEventPersistenceAdapter adapter;

    @Test
    void alreadyProcessed_flipsFromFalseToTrueAfterMark() {
        UUID eventId = UUID.randomUUID();
        assertThat(adapter.alreadyProcessed(eventId)).isFalse();

        adapter.markProcessed(eventId, "payment.completed");

        assertThat(adapter.alreadyProcessed(eventId)).isTrue();
        assertThat(adapter.alreadyProcessed(UUID.randomUUID())).isFalse();
    }
    @Test
    void markProcessed_duplicateMustFailInsteadOfUpdatingExistingMarker() {
        UUID eventId = UUID.randomUUID();
        adapter.markProcessed(eventId, "payment.completed");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.markProcessed(eventId, "payment.completed"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

}
