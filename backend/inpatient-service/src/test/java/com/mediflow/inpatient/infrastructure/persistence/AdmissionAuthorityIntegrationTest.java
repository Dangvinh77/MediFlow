package com.mediflow.inpatient.infrastructure.persistence;

import com.mediflow.inpatient.application.service.LookupAdmissionAuthorityService;
import com.mediflow.inpatient.infrastructure.persistence.adapter.AdmissionAuthorityPersistenceAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Clock;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class AdmissionAuthorityIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG=new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void lookup_committedAdmissionAndMedicalDischarge_preservesExactIdsAndRevision() {
        Flyway.configure().dataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()).load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword()));
        var service=new LookupAdmissionAuthorityService(new AdmissionAuthorityPersistenceAdapter(jdbc),Clock.systemUTC());
        UUID id=UUID.randomUUID(),patient=UUID.randomUUID(),department=UUID.randomUUID(),record=UUID.randomUUID();
        assertThat(service.lookup(id).exists()).isFalse();
        jdbc.update("""
                INSERT INTO dot_noi_tru(admission_id,admission_request_id,patient_id,source_record_id,
                requested_by,diagnosis_summary,requested_at,department_id,priority,status,version,admitted_at)
                VALUES(?,?,?,?,?,'Test',now(),?,'ROUTINE','ADMITTED',1,now())
                """,id,UUID.randomUUID(),patient,record,UUID.randomUUID(),department);
        var active=service.lookup(id);
        assertThat(active.eligible()).isTrue();
        assertThat(active.patientId()).isEqualTo(patient);
        assertThat(active.departmentId()).isEqualTo(department);
        assertThat(active.sourceRecordId()).isEqualTo(record);
        assertThat(active.sourceRevision()).isEqualTo("1");
        jdbc.update("UPDATE dot_noi_tru SET status='MEDICALLY_DISCHARGED',medically_discharged_at=now(),version=2 WHERE admission_id=?",id);
        var discharged=service.lookup(id);
        assertThat(discharged.eligible()).isFalse();
        assertThat(discharged.status()).isEqualTo("MEDICALLY_DISCHARGED");
        assertThat(discharged.sourceRevision()).isEqualTo("2");
    }
}
