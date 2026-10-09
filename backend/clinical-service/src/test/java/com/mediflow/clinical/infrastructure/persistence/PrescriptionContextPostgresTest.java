package com.mediflow.clinical.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mediflow.clinical.infrastructure.persistence.adapter.PrescriptionContextReadAdapter;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "mediflow.clinical.prescription-context-lookup.enabled=true")
@Import(PrescriptionContextReadAdapter.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PrescriptionContextPostgresTest {
    @Container @ServiceConnection static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");
    @Autowired JdbcTemplate jdbc;
    @Autowired PrescriptionContextReadAdapter contexts;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private static UUID id(int last) { return UUID.fromString("09000000-0000-0000-0000-" + String.format("%012d", last)); }

    @ParameterizedTest @CsvSource({"appointment.json,true", "walk-in.json,false"})
    void lookup_exactOwnedRelationshipMatchesProducerFixture(String file, boolean appointment) throws Exception {
        if (appointment) jdbc.update("""
                INSERT INTO appointment(appointment_id,patient_id,doctor_id,department_id,appointment_date,appointment_time,status)
                VALUES (?,?,?,?,'2026-10-09','09:00','ARRIVED')
                """, id(5), id(2), id(3), id(4));
        jdbc.update("""
                INSERT INTO medical_record(record_id,patient_id,doctor_id,department_id,examination_date,appointment_id,
                    status,disposition,completed_at,symptoms,disposition_note)
                VALUES (?,?,?,?,'2026-10-09',?,?,?,?::timestamptz,'PRIVATE SYMPTOMS','PRIVATE NOTE')
                """, id(1), id(2), id(3), id(4), appointment ? id(5) : null,
                appointment ? "COMPLETED" : "OPEN", appointment ? "PRESCRIPTION" : null,
                appointment ? "2026-10-09T01:00:00Z" : null);
        var actual = mapper.valueToTree(contexts.find(id(1)));
        var expected = mapper.readTree(getClass().getResourceAsStream("/contracts/prescription-context-v1/" + file)).get("data");
        ((ObjectNode) actual).remove("observedAt"); ((ObjectNode) expected).remove("observedAt");
        assertThat(actual).isEqualTo(expected);
        assertThat(actual.toString()).doesNotContain("PRIVATE", "symptoms", "diagnos", "note");
    }

    @Test void lookup_missingEchoesExactRecordWithoutSelectingAnotherRecordForPatient() throws Exception {
        var actual = contexts.find(id(1));
        assertThat(actual.exists()).isFalse(); assertThat(actual.recordId()).isEqualTo(id(1));
        assertThat(actual.patientId()).isNull(); assertThat(actual.careEpisodeId()).isNull();
        assertThat(actual.observedAt()).isBetween(Instant.now().minusSeconds(5), Instant.now().plusSeconds(5));
        var expected = mapper.readTree(getClass().getResourceAsStream("/contracts/prescription-context-v1/missing.json")).get("data");
        var tree = (ObjectNode) mapper.valueToTree(actual); tree.remove("observedAt"); ((ObjectNode) expected).remove("observedAt");
        assertThat(tree).isEqualTo(expected);
    }
    @Test void lookup_inconsistentAppointmentPatientDoesNotInventEpisode() {
        jdbc.update("INSERT INTO appointment(appointment_id,patient_id,doctor_id,department_id,appointment_date,appointment_time) VALUES (?,?,?,?,'2026-10-09','09:00')", id(5), id(7), id(3), id(4));
        jdbc.update("INSERT INTO medical_record(record_id,patient_id,doctor_id,department_id,examination_date,appointment_id) VALUES (?,?,?,?,'2026-10-09',?)", id(1), id(2), id(3), id(4), id(5));
        assertThatThrownBy(() -> contexts.find(id(1))).isInstanceOf(DataAccessException.class);
    }
}
