package com.mediflow.inpatient.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.inpatient.application.port.out.AdmissionRepositoryPort;
import com.mediflow.inpatient.domain.model.Admission;
import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import com.mediflow.inpatient.infrastructure.persistence.adapter.AdmissionPersistenceAdapter;
import com.mediflow.inpatient.infrastructure.persistence.mapper.InpatientPersistenceMapper;
import com.mediflow.inpatient.infrastructure.persistence.repository.AdmissionJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.AdmissionStatusHistoryJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.FinancialClearanceJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.AdmissionStatusHistoryJpaEntity;
import com.mediflow.inpatient.domain.model.AdmissionStatusHistory;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.FinancialClearanceJpaEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdmissionPersistenceAdapter.class, InpatientPersistenceMapper.class})
@Testcontainers(disabledWithoutDocker = true)
class InpatientJpaSchemaIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-27T04:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inpatient_jpa_test")
            .withUsername("inpatient")
            .withPassword("inpatient");

    @Autowired
    private AdmissionJpaRepository admissions;

    @Autowired
    private AdmissionRepositoryPort admissionStore;

    @Autowired
    private AdmissionStatusHistoryJpaRepository histories;

    @Autowired
    private FinancialClearanceJpaRepository clearances;

    @Autowired
    private InpatientPersistenceMapper mapper;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void hibernateMappingsValidateAgainstTheFlywaySchema() {
        assertThat(admissions).isNotNull();
    }

    @Test
    void admissionIsFlushedBeforeForeignKeyHistoryAndClearanceExpiryIsRehydrated() {
        UUID admissionId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        Admission admission = Admission.create(admissionId, UUID.randomUUID(), patientId,
                UUID.randomUUID(), UUID.randomUUID(), "Pneumonia", NOW, UUID.randomUUID(),
                AdmissionPriority.ROUTINE, false).markAwaitingBed();

        admissionStore.saveAndFlush(admission);
        AdmissionStatusHistory history = new AdmissionStatusHistory(UUID.randomUUID(), admissionId,
                null, AdmissionStatus.REQUESTED, UUID.randomUUID(), "Admission requested", "corr-1", NOW);
        histories.saveAndFlush(mapper.copy(history, new AdmissionStatusHistoryJpaEntity()));

        UUID clearanceId = UUID.randomUUID();
        Instant expiresAt = NOW.plusSeconds(3600);
        FinancialClearanceJpaEntity clearance = new FinancialClearanceJpaEntity();
        clearance.maXacNhan = clearanceId;
        clearance.maSuKien = UUID.randomUUID();
        clearance.maHoaDon = UUID.randomUUID();
        clearance.maTaiKhoan = UUID.randomUUID();
        clearance.maDotNoiTru = admissionId;
        clearance.maBenhNhan = patientId;
        clearance.soTien = new BigDecimal("125000.00");
        clearance.tienTe = "VND";
        clearance.phuongThucThanhToan = "CASH";
        clearance.hetHanLuc = expiresAt;
        clearance.thoiGianCap = NOW;
        clearances.saveAndFlush(clearance);

        admission.applyFinancialClearance(clearanceId, expiresAt, false, NOW);
        admissionStore.saveAndFlush(admission);

        assertThat(admissionStore.findById(admissionId)).get()
                .extracting(Admission::depositExpiresAt)
                .isEqualTo(expiresAt);
    }
}
