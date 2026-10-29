package com.mediflow.patient.infrastructure.persistence;

import com.mediflow.common.api.PageQuery;
import com.mediflow.patient.domain.model.Gender;
import com.mediflow.patient.domain.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({PatientPersistenceAdapter.class, PatientPersistenceMapper.class})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop",
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
        assertThat(adapter.existsByIdentityNumberAndPatientIdNot("123", id)).isFalse();
        assertThat(adapter.existsByIdentityNumberAndPatientIdNot("123", UUID.randomUUID())).isTrue();
        assertThat(adapter.search("nguyen", new PageQuery(0, 20)).content()).hasSize(1);
        assertThat(adapter.search("missing", new PageQuery(0, 20)).content()).isEmpty();
    }

    @Test
    void savesChecksIdentityAndDeletesPatient() {
        Patient patient = Patient.create("Nguyen Van C", LocalDate.of(1988, 2, 2), Gender.M,
                "456", "Hanoi", "0900000002", "c@example.com", null);

        adapter.save(patient);

        assertThat(adapter.existsByIdentityNumber("456")).isTrue();
        assertThat(adapter.findById(patient.patientId())).isPresent();
        adapter.deleteById(patient.patientId());
        assertThat(adapter.findById(patient.patientId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  Paging  ", "910"})
    void searchPagesNewestFirstWithUuidTieBreaker(String keyword) {
        Instant recent = Instant.parse("2026-10-29T00:00:00Z");
        // Deliberately insert out of order, including equal timestamps across page boundaries.
        for (int number : List.of(4, 2, 6, 1, 5, 3)) {
            repository.save(new PatientJpaEntity(new UUID(0, number), "Paging " + number,
                    LocalDate.of(1988, 2, 2), Gender.M, "910" + number, null, null, null, null,
                    number == 5 ? recent.minusSeconds(1) : recent, null).withArchived(number == 6));
        }
        repository.flush();

        for (int repeat = 0; repeat < 2; repeat++) {
            var first = adapter.search(keyword, new PageQuery(0, 2));
            var second = adapter.search(keyword, new PageQuery(1, 2));
            var last = adapter.search(keyword, new PageQuery(2, 2));
            assertThat(first.totalElements()).isEqualTo(5);
            assertThat(first.content()).extracting(Patient::patientId)
                    .containsExactly(new UUID(0, 1), new UUID(0, 2));
            assertThat(second.content()).extracting(Patient::patientId)
                    .containsExactly(new UUID(0, 3), new UUID(0, 4));
            assertThat(last.content()).extracting(Patient::patientId).containsExactly(new UUID(0, 5));
            assertThat(adapter.search(keyword, new PageQuery(3, 2)).content()).isEmpty();
        }
    }
}
