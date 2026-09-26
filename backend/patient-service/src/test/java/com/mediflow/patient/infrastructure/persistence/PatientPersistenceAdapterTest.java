package com.mediflow.patient.infrastructure.persistence;

import com.mediflow.common.api.PageQuery;
import com.mediflow.patient.domain.model.Gender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({PatientPersistenceAdapter.class, PatientPersistenceMapper.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:patient;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class PatientPersistenceAdapterTest {
    @Autowired PatientPersistenceAdapter adapter;
    @Autowired PatientJpaRepository repository;

    @Test
    void findsSearchesAndChecksExistenceUsingEnglishDbColumns() {
        UUID id = UUID.randomUUID();
        repository.save(new PatientJpaEntity(id, "Nguyen Van B", LocalDate.of(1988, 2, 2), Gender.M,
                "123", "Hanoi", "0900000001", "b@example.com", null,
                Instant.parse("2026-01-01T00:00:00Z"), null));

        assertThat(adapter.findById(id)).isPresent();
        assertThat(adapter.existsById(id)).isTrue();
        assertThat(adapter.search("nguyen", new PageQuery(0, 20)).content()).hasSize(1);
        assertThat(adapter.search("missing", new PageQuery(0, 20)).content()).isEmpty();
    }
}
