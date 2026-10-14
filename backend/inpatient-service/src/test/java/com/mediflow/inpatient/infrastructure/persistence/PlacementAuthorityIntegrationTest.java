package com.mediflow.inpatient.infrastructure.persistence;

import com.mediflow.inpatient.application.service.LookupPlacementAuthorityService;
import com.mediflow.inpatient.infrastructure.persistence.adapter.PlacementAuthorityPersistenceAdapter;
import java.time.Clock;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class PlacementAuthorityIntegrationTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void lookupReadsCurrentBedDepartmentRatherThanAdmissionOwningDepartment() {
        Flyway.configure().dataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()).load().migrate();
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
        var service = new LookupPlacementAuthorityService(
                new PlacementAuthorityPersistenceAdapter(jdbc), Clock.systemUTC());
        UUID admissionId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID admissionDepartment = UUID.randomUUID();
        UUID bedDepartment = UUID.randomUUID();
        UUID bedId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO dot_noi_tru(admission_id,admission_request_id,patient_id,source_record_id,
                requested_by,diagnosis_summary,requested_at,department_id,priority,status,version,
                placement_revision,admitted_at)
                VALUES(?,?,?,?,?,'Test',now(),?,'ROUTINE','ADMITTED',1,3,now())
                """, admissionId, UUID.randomUUID(), patientId, UUID.randomUUID(),
                UUID.randomUUID(), admissionDepartment);
        jdbc.update("""
                INSERT INTO giuong_benh(bed_id,department_id,ward_code,room_code,bed_code,bed_type,
                status,active,version,capacity_revision)
                VALUES(?,?,'WARD-B','101','01','GENERAL','OCCUPIED',true,0,1)
                """, bedId, bedDepartment);
        jdbc.update("""
                INSERT INTO phan_giuong(assignment_id,admission_id,bed_id,assigned_by,assigned_at,status)
                VALUES(?,?,?,?,now(),'ACTIVE')
                """, assignmentId, admissionId, bedId, UUID.randomUUID());

        var result = service.lookup(admissionId);

        assertThat(result.patientId()).isEqualTo(patientId);
        assertThat(result.placementRevision()).isEqualTo(3);
        assertThat(result.placement().departmentId()).isEqualTo(bedDepartment);
        assertThat(result.placement().departmentId()).isNotEqualTo(admissionDepartment);
    }
}
