package com.mediflow.lab.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
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

import com.mediflow.lab.domain.model.LabResult;
import com.mediflow.lab.domain.model.LabTest;
import com.mediflow.lab.infrastructure.persistence.adapter.LabTestPersistenceAdapter;
import com.mediflow.lab.infrastructure.persistence.adapter.ProcessedEventPersistenceAdapter;
import com.mediflow.common.api.PageQuery;
import com.mediflow.lab.domain.model.LabTestStatus;

@DataJpaTest
@Import({LabTestPersistenceAdapter.class, ProcessedEventPersistenceAdapter.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class LabPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired LabTestPersistenceAdapter tests;
    @Autowired ProcessedEventPersistenceAdapter processedEvents;

    @Test
    void save_newAggregate_returnsDatabaseGeneratedTestAndResultIds() {
        LabTest pending = LabTest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", LocalDate.now());
        pending.recordResults(List.of(LabResult.create("WBC", "7.5", "10^9/L", "4.0-10.0")),
                "Normal", LocalDate.now());

        LabTest saved = tests.save(pending);

        assertThat(saved.getTestId()).isNotNull();
        assertThat(saved.getResults()).singleElement()
                .extracting(LabResult::getResultId)
                .isNotNull();
        assertThat(tests.findById(saved.getTestId())).get()
                .satisfies(found -> assertThat(found.getResults()).singleElement()
                        .extracting(LabResult::getResultId)
                        .isEqualTo(saved.getResults().get(0).getResultId()));
    }

    @Test
    void save_existingAggregate_preservesGeneratedTestAndResultIds() {
        LabTest pending = LabTest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", LocalDate.now());
        pending.recordResults(List.of(LabResult.create("WBC", "7.5", "10^9/L", "4.0-10.0")),
                "Normal", LocalDate.now());

        LabTest saved = tests.save(pending);
        UUID testId = saved.getTestId();
        UUID resultId = saved.getResults().get(0).getResultId();

        saved.markPaid();
        LabTest updated = tests.save(saved);

        assertThat(updated.getTestId()).isEqualTo(testId);
        assertThat(updated.getResults()).singleElement()
                .extracting(LabResult::getResultId)
                .isEqualTo(resultId);
        assertThat(tests.findById(testId)).get()
                .satisfies(found -> {
                    assertThat(found.isPaid()).isTrue();
                    assertThat(found.getResults()).singleElement()
                            .extracting(LabResult::getResultId)
                            .isEqualTo(resultId);
                });
    }

    @Test
    void completedTestRoundTrip_preservesResultsAndPaidFlag() {
        LabTest test = LabTest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "CBC", LocalDate.now());
        test.recordResults(List.of(LabResult.create("WBC", "7.5", "10^9/L", "4.0-10.0")),
                "Normal", LocalDate.now());
        test.markPaid();

        LabTest found = tests.findById(tests.save(test).getTestId()).orElseThrow();

        assertThat(found.isPaid()).isTrue();
        assertThat(found.getResults()).singleElement()
                .satisfies(result -> assertThat(result.getValue()).isEqualTo("7.5"));
        assertThat(tests.findByIdForUpdate(found.getTestId())).isPresent();
    }

    @Test
    void processedEvent_firstClaim_returnsTrue() {
        UUID eventId = UUID.randomUUID();
        assertThat(processedEvents.tryClaim(eventId, "medical-record.created")).isTrue();
    }

    @Test
    void processedEvent_duplicateClaim_returnsFalse() {
        UUID eventId = UUID.randomUUID();
        assertThat(processedEvents.tryClaim(eventId, "medical-record.created")).isTrue();

        assertThat(processedEvents.tryClaim(eventId, "medical-record.created")).isFalse();
    }

    @Test
    void queriesFilterByPatientRecordDepartmentAndStatus() {
        UUID patient = UUID.randomUUID();
        UUID record = UUID.randomUUID();
        UUID department = UUID.randomUUID();
        LabTest matching = tests.save(LabTest.create(record, patient, department, "CBC", LocalDate.now()));
        tests.save(LabTest.create(UUID.randomUUID(), patient, UUID.randomUUID(), "Glucose", LocalDate.now()));

        assertThat(tests.findByPatient(patient)).hasSize(2);
        assertThat(tests.findByRecord(record)).extracting(LabTest::getTestId)
                .containsExactly(matching.getTestId());
        assertThat(tests.search(department, LabTestStatus.PENDING, new PageQuery(0, 10)).content())
                .extracting(LabTest::getTestId).containsExactly(matching.getTestId());
        assertThat(tests.search(department, null, new PageQuery(0, 10)).totalElements()).isEqualTo(1);
        assertThat(tests.search(null, LabTestStatus.PENDING, new PageQuery(0, 10)).totalElements()).isEqualTo(2);
        assertThat(tests.search(null, null, new PageQuery(0, 10)).totalElements()).isEqualTo(2);
    }
}
