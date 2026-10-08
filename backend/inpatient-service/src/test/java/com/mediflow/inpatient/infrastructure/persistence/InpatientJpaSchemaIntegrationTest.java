package com.mediflow.inpatient.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.inpatient.application.dto.request.TransferBedRequest;
import com.mediflow.inpatient.application.mapper.InpatientDtoMapper;
import com.mediflow.inpatient.application.port.out.AdmissionRepositoryPort;
import com.mediflow.inpatient.application.port.out.BedAssignmentRepositoryPort;
import com.mediflow.inpatient.application.port.out.BedRepositoryPort;
import com.mediflow.inpatient.application.port.out.ClinicalOrderReferenceRepositoryPort;
import com.mediflow.inpatient.application.port.out.DepositSuggestionPolicyPort;
import com.mediflow.inpatient.application.port.out.DischargeSummaryRepositoryPort;
import com.mediflow.inpatient.application.port.out.InpatientEventStorePort;
import com.mediflow.inpatient.application.port.out.InpatientOutboxPort;
import com.mediflow.inpatient.application.port.out.ProcessedEventPort;
import com.mediflow.inpatient.application.port.out.SurgeryEventReceiptRepositoryPort;
import com.mediflow.inpatient.application.port.out.TreatmentEntryRepositoryPort;
import com.mediflow.inpatient.application.service.InpatientApplicationService;
import com.mediflow.inpatient.domain.model.Admission;
import com.mediflow.inpatient.domain.model.Bed;
import com.mediflow.inpatient.domain.model.BedAssignment;
import com.mediflow.inpatient.domain.model.AdmissionStatusHistory;
import com.mediflow.inpatient.domain.model.SurgeryEventReceipt;
import com.mediflow.inpatient.domain.model.enums.AdmissionPriority;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import com.mediflow.inpatient.domain.model.enums.ExternalOrderStatus;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.AdmissionStatusHistoryJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.jpaEntity.FinancialClearanceJpaEntity;
import com.mediflow.inpatient.infrastructure.persistence.adapter.AdmissionPersistenceAdapter;
import com.mediflow.inpatient.infrastructure.persistence.adapter.BedPersistenceAdapter;
import com.mediflow.inpatient.infrastructure.persistence.adapter.SurgeryEventReceiptPersistenceAdapter;
import com.mediflow.inpatient.infrastructure.persistence.mapper.InpatientPersistenceMapper;
import com.mediflow.inpatient.infrastructure.persistence.repository.AdmissionJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.AdmissionStatusHistoryJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.BedAssignmentJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.FinancialClearanceJpaRepository;
import com.mediflow.inpatient.infrastructure.persistence.repository.SurgeryEventReceiptJpaRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdmissionPersistenceAdapter.class, BedPersistenceAdapter.class,
        SurgeryEventReceiptPersistenceAdapter.class, InpatientPersistenceMapper.class})
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
    private BedRepositoryPort bedStore;

    @Autowired
    private BedAssignmentRepositoryPort assignmentStore;

    @Autowired
    private BedAssignmentJpaRepository assignmentRows;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdmissionStatusHistoryJpaRepository histories;

    @Autowired
    private FinancialClearanceJpaRepository clearances;

    @Autowired
    private InpatientPersistenceMapper mapper;

    @Autowired
    private SurgeryEventReceiptRepositoryPort surgeryReceiptStore;

    @Autowired
    private SurgeryEventReceiptJpaRepository surgeryReceiptRows;

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

    @Test
    void admittedTransferFlushesReleasedAssignmentBeforeCreatingTheNewActiveAssignment() {
        UUID admissionId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID oldBedId = UUID.randomUUID();
        UUID targetBedId = UUID.randomUUID();
        UUID clearanceId = UUID.randomUUID();
        Instant transferredAt = NOW.plusSeconds(60);
        Admission admitted = Admission.create(admissionId, UUID.randomUUID(), patientId,
                        UUID.randomUUID(), actorId, "Pneumonia", NOW, UUID.randomUUID(),
                        AdmissionPriority.ROUTINE, false)
                .markAwaitingBed();
        admissionStore.saveAndFlush(admitted);

        FinancialClearanceJpaEntity clearance = new FinancialClearanceJpaEntity();
        clearance.maXacNhan = clearanceId;
        clearance.maSuKien = UUID.randomUUID();
        clearance.maHoaDon = UUID.randomUUID();
        clearance.maTaiKhoan = UUID.randomUUID();
        clearance.maDotNoiTru = admissionId;
        clearance.maBenhNhan = patientId;
        clearance.soTien = new BigDecimal("100000.00");
        clearance.tienTe = "VND";
        clearance.phuongThucThanhToan = "CASH";
        clearance.thoiGianCap = NOW;
        clearances.saveAndFlush(clearance);
        admitted.applyFinancialClearance(clearanceId, null, true, NOW)
                .admit(NOW.plusSeconds(1), true, null);
        admissionStore.saveAndFlush(admitted);

        bedStore.save(Bed.create(oldBedId, UUID.randomUUID(), "W1", "R1", "B1", "GENERAL").assign());
        bedStore.save(Bed.create(targetBedId, UUID.randomUUID(), "W1", "R1", "B2", "GENERAL"));
        assignmentStore.save(BedAssignment.create(UUID.randomUUID(), admissionId, oldBedId, actorId, NOW));
        assignmentRows.flush();

        ClinicalOrderReferenceRepositoryPort references = mock(ClinicalOrderReferenceRepositoryPort.class);
        when(references.findByAdmissionId(admissionId)).thenReturn(List.of());
        var service = new InpatientApplicationService(admissionStore, bedStore, assignmentStore,
                mock(TreatmentEntryRepositoryPort.class), references,
                mock(DischargeSummaryRepositoryPort.class), mock(ProcessedEventPort.class),
                mock(SurgeryEventReceiptRepositoryPort.class),
                mock(InpatientEventStorePort.class), mock(InpatientOutboxPort.class),
                mock(DepositSuggestionPolicyPort.class), mock(InpatientDtoMapper.class),
                Clock.fixed(transferredAt, ZoneOffset.UTC));

        service.transfer(admissionId,
                new TransferBedRequest(targetBedId, actorId, "Move to another room"), "correlation-1");

        Integer activeAssignments = jdbcTemplate.queryForObject(
                "select count(*) from phan_giuong where admission_id = ? and status = 'ACTIVE'",
                Integer.class, admissionId);
        assertThat(activeAssignments).isEqualTo(1);
        assertThat(assignmentStore.findActiveByAdmissionId(admissionId)).get()
                .extracting(BedAssignment::bedId).isEqualTo(targetBedId);
        assertThat(admissionStore.findById(admissionId)).get()
                .extracting(Admission::status).isEqualTo(AdmissionStatus.ADMITTED);
    }

    @Test
    void surgeryReceiptAdapterReloadsExactIdentityAndEnforcesSemanticUniqueness() {
        UUID admissionId = UUID.randomUUID();
        Admission admission = Admission.create(admissionId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "Surgery", NOW, UUID.randomUUID(),
                AdmissionPriority.ROUTINE, false);
        admissionStore.saveAndFlush(admission);
        UUID operationId = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        SurgeryEventReceipt receipt = SurgeryEventReceipt.pending(UUID.randomUUID(),
                "surgery.completed", operationId, "a".repeat(64), caseId, requestId,
                admissionId, admission.patientId(), admission.departmentId(), 3, 1,
                ExternalOrderStatus.COMPLETED, null, "Surgery completed", NOW, NOW);

        SurgeryEventReceipt saved = surgeryReceiptStore.save(receipt);
        surgeryReceiptRows.flush();

        assertThat(surgeryReceiptStore.findByEventId(saved.eventId())).get()
                .extracting(SurgeryEventReceipt::surgeryRequestId).isEqualTo(requestId);
        assertThat(surgeryReceiptStore.findByBusinessOperation("surgery.completed", operationId))
                .get().extracting(SurgeryEventReceipt::receiptId).isEqualTo(saved.receiptId());
        assertThat(surgeryReceiptStore.findByCaseId(caseId))
                .extracting(SurgeryEventReceipt::receiptId).containsExactly(saved.receiptId());

        SurgeryEventReceipt duplicate = SurgeryEventReceipt.pending(UUID.randomUUID(),
                "surgery.completed", operationId, "b".repeat(64), caseId, requestId,
                admissionId, admission.patientId(), admission.departmentId(), 4, 2,
                ExternalOrderStatus.COMPLETED, null, "Surgery completed again", NOW, NOW);
        surgeryReceiptStore.save(duplicate);

        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class,
                surgeryReceiptRows::flush);
    }
}
