package com.mediflow.clinical.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.clinical.infrastructure.persistence.adapter.OutpatientRegistrationPersistenceAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DepartmentQueueNumberPostgresTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    static JdbcTemplate jdbc;
    static ClinicIntakeTestFixtures fixtures;
    static OutpatientRegistrationPersistenceAdapter adapter;
    static final LocalDate DAY = LocalDate.of(2026, 11, 10);

    @BeforeAll static void migrate() {
        var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        fixtures = new ClinicIntakeTestFixtures(jdbc);
        adapter = new OutpatientRegistrationPersistenceAdapter(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    @BeforeEach void clear() {
        jdbc.execute("TRUNCATE consultation_session, appointment, department_queue_counter CASCADE");
    }

    @Test void eachDepartmentHasItsOwnSequence() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertThat(adapter.allocateDepartmentQueueNumber(a, DAY)).isEqualTo(1);
        assertThat(adapter.allocateDepartmentQueueNumber(a, DAY)).isEqualTo(2);
        assertThat(adapter.allocateDepartmentQueueNumber(b, DAY)).isEqualTo(1);
    }

    @Test void sequenceRestartsOnAnotherDate() {
        UUID a = UUID.randomUUID();
        adapter.allocateDepartmentQueueNumber(a, DAY);
        adapter.allocateDepartmentQueueNumber(a, DAY);
        assertThat(adapter.allocateDepartmentQueueNumber(a, DAY.plusDays(1))).isEqualTo(1);
    }

    @Test void continuesAfterHighestExistingNumberWhenFlagTurnsOnMidDay() {
        UUID a = UUID.randomUUID();
        fixtures.confirmedWithNumber(fixtures.session(a, DAY), 5);
        fixtures.confirmedWithNumber(fixtures.session(a, DAY), 7);
        fixtures.confirmedWithNumber(fixtures.session(a, DAY.plusDays(1)), 50);
        fixtures.confirmedWithNumber(fixtures.session(UUID.randomUUID(), DAY), 60);
        assertThat(adapter.allocateDepartmentQueueNumber(a, DAY)).isEqualTo(8);
        assertThat(adapter.allocateDepartmentQueueNumber(a, DAY)).isEqualTo(9);
    }

    @Test void sessionSequenceSkipsNumbersIssuedWhileDepartmentSequenceWasOn() {
        UUID a = UUID.randomUUID();
        var session = fixtures.session(a, DAY);
        fixtures.confirmedWithNumber(session, adapter.allocateDepartmentQueueNumber(a, DAY));
        fixtures.confirmedWithNumber(session, adapter.allocateDepartmentQueueNumber(a, DAY));
        assertThat(adapter.allocateQueueNumber(session.sessionId())).isEqualTo(3);
        assertThat(adapter.allocateQueueNumber(session.sessionId())).isEqualTo(4);
    }

    @Test void departmentSequenceSkipsNumbersIssuedWhileSessionSequenceWasOn() {
        UUID a = UUID.randomUUID();
        var session = fixtures.session(a, DAY);
        fixtures.confirmedWithNumber(session, adapter.allocateDepartmentQueueNumber(a, DAY));
        fixtures.confirmedWithNumber(session, 2);
        fixtures.confirmedWithNumber(session, 3);
        assertThat(adapter.allocateDepartmentQueueNumber(a, DAY)).isEqualTo(4);
        assertThat(adapter.allocateDepartmentQueueNumber(a, DAY)).isEqualTo(5);
    }

    @Test void concurrentAllocationsNeverRepeat() throws Exception {
        UUID a = UUID.randomUUID();
        var tasks = new ArrayList<Callable<Integer>>();
        IntStream.range(0, 20).forEach(i -> tasks.add(() -> adapter.allocateDepartmentQueueNumber(a, DAY)));
        var numbers = new ArrayList<Integer>();
        try (var pool = Executors.newFixedThreadPool(8)) {
            for (var future : pool.invokeAll(tasks)) numbers.add(future.get());
        }
        assertThat(numbers).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 20).boxed().toList());
    }
}
