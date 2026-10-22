package com.mediflow.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.application.port.in.*;
import com.mediflow.organization.domain.model.*;
import com.mediflow.organization.infrastructure.config.OrganizationDemoSeeder;
import com.mediflow.patient.application.dto.request.CreatePatientRequest;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.port.in.CreatePatientUseCase;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.infrastructure.config.PatientDemoSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real receipt transactions in two H2 stores; business ports are mocked.
 * PostgreSQL advisory-lock concurrency and full outbox integration require Docker acceptance. */
public class LocalDemoSeedTest {
    private final GetDepartmentUseCase getDepartments = mock(GetDepartmentUseCase.class);
    private final CreateDepartmentUseCase createDepartment = mock(CreateDepartmentUseCase.class);
    private final GetStaffUseCase getStaff = mock(GetStaffUseCase.class);
    private final CreateStaffUseCase createStaff = mock(CreateStaffUseCase.class);
    private final ManageAccountsUseCase getAccounts = mock(ManageAccountsUseCase.class);
    private final CreateAccountUseCase createAccount = mock(CreateAccountUseCase.class);
    private final GetPatientUseCase getPatients = mock(GetPatientUseCase.class);
    private final CreatePatientUseCase createPatient = mock(CreatePatientUseCase.class);
    private final List<Department> departments = new ArrayList<>();
    private final List<Staff> staff = new ArrayList<>();
    private final List<Account> accounts = new ArrayList<>();
    private final List<PatientDTO> patients = new ArrayList<>();
    private OrganizationDemoSeeder organization;
    private PatientDemoSeeder patient;
    private JdbcTemplate orgJdbc;
    private JdbcTemplate patientJdbc;
    private JsonNode data;

    // H2 has no PostgreSQL transaction advisory locks. This alias is only a syntax stand-in.
    public static void advisoryLock(long key) { }

    @BeforeEach void setup() throws Exception {
        orgJdbc = database();
        patientJdbc = database();
        organization = new OrganizationDemoSeeder(orgJdbc, transactions(orgJdbc), getDepartments,
                createDepartment, getStaff, createStaff, getAccounts, createAccount);
        patient = new PatientDemoSeeder(patientJdbc, transactions(patientJdbc), getPatients, createPatient);
        try (var input = getClass().getResourceAsStream("/demo-seed.json")) {
            data = new ObjectMapper().readTree(input);
        }
        when(getDepartments.getAllDepartments()).thenReturn(departments);
        when(getStaff.search(isNull(), isNull(), any())).thenAnswer(call ->
                page(staff, call.getArgument(2)));
        when(getAccounts.search(isNull(), isNull(), isNull(), any())).thenAnswer(call ->
                page(accounts, call.getArgument(3)));
        when(getPatients.search(isNull(), any())).thenAnswer(call ->
                page(patients, call.getArgument(1)));
        when(createDepartment.execute(anyString(), anyString(), any(), anyString(), any(DepartmentProfile.class))).thenAnswer(call -> {
            var row = Department.create(UUID.randomUUID(), call.getArgument(0), call.getArgument(1),
                    call.getArgument(2), call.getArgument(3)).withProfile(call.getArgument(4));
            departments.add(row);
            return row;
        });
        when(createStaff.execute(anyString(), any(), any(), nullable(String.class), nullable(String.class),
                anyString(), anyString(), anyString(), any(StaffProfile.class))).thenAnswer(call -> {
            var row = Staff.create(call.getArgument(0), call.getArgument(1), call.getArgument(2),
                    call.getArgument(3), call.getArgument(4), call.getArgument(5), call.getArgument(6), call.getArgument(7)).withProfile(call.getArgument(8));
            staff.add(row);
            return row;
        });
        when(createAccount.execute(anyString(), anyString(), nullable(UUID.class), nullable(UUID.class), any()))
                .thenAnswer(call -> {
                    var row = Account.create(UUID.randomUUID(), call.getArgument(0), "hashed-in-business-port",
                            call.getArgument(2), call.getArgument(3), call.getArgument(4));
                    accounts.add(row);
                    return row;
                });
        when(createPatient.create(any())).thenAnswer(call -> {
            return addPatient(call.getArgument(0));
        });
    }

    @Test void freshDatasetHasExactCountsAndAuthoritativePatientLinks() {
        runSeed();
        assertThat(departments).hasSize(10);
        assertThat(staff).hasSize(20);
        assertThat(departments).allSatisfy(row -> assertThat(row.getProfile().description()).isNotBlank());
        assertThat(staff).allSatisfy(row -> assertThat(row.getProfile().qualification()).isNotBlank());
        assertThat(patients).hasSize(10);
        assertThat(accounts).hasSize(10);
        assertThat(accounts.stream().filter(a -> a.getRole() == Role.PATIENT).map(Account::getPatientId))
                .containsExactly(patients.get(0).maBenhNhan(), patients.get(1).maBenhNhan());
        assertThat(count(orgJdbc)).isEqualTo(40);
        assertThat(count(patientJdbc)).isEqualTo(10);
    }

    @Test void rerunPreservesPasswordAndDisabledAccountWithoutAnyCreates() {
        runSeed();
        Account admin = accounts.getFirst();
        admin.changePassword("user-changed-hash");
        admin.deactivate();
        clearInvocations(createDepartment, createStaff, createPatient, createAccount);
        runSeed();
        verifyNoInteractions(createDepartment, createStaff, createPatient, createAccount);
        assertThat(admin.getPasswordHash()).isEqualTo("user-changed-hash");
        assertThat(admin.isActive()).isFalse();
    }

    @Test void changedOrDeletedNaturalIdentitiesAreNotRecreatedAfterRestart() {
        runSeed();
        // The seed must use durable receipts even when original natural-key lookups find nothing.
        departments.clear();
        staff.clear();
        patients.clear();
        accounts.clear();
        clearInvocations(createDepartment, createStaff, createPatient, createAccount);
        runSeed();
        verifyNoInteractions(createDepartment, createStaff, createPatient, createAccount);
        assertThat(count(orgJdbc)).isEqualTo(40);
        assertThat(count(patientJdbc)).isEqualTo(10);
    }

    @Test void preexistingRowsAreAdoptedWithoutResettingPassword() {
        runSeed();
        accounts.getFirst().changePassword("preserved-preexisting-hash");
        orgJdbc.update("delete from local_demo_seed_receipt");
        patientJdbc.update("delete from local_demo_seed_receipt");
        clearInvocations(createDepartment, createStaff, createPatient, createAccount);
        runSeed();
        verifyNoInteractions(createDepartment, createStaff, createPatient, createAccount);
        assertThat(accounts.getFirst().getPasswordHash()).isEqualTo("preserved-preexisting-hash");
    }

    @Test void conflictingAccountIsNotReassigned() {
        var ids = organization.seedMasters(data);
        var patientIds = patient.seed(data);
        accounts.add(Account.create(UUID.randomUUID(), "demo.admin", "original-hash", UUID.randomUUID(), Role.ADMIN));
        assertThatThrownBy(() -> organization.seedAccounts(data, ids, patientIds))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("identity conflict");
        verifyNoInteractions(createAccount);
        assertThat(count(orgJdbc)).isEqualTo(30);
    }

    @Test void patientFailureRollsBackReceiptsAndNextStartupResumes() {
        organization.seedMasters(data);
        doAnswer(call -> {
            if (!patients.isEmpty()) throw new IllegalStateException("simulated outage");
            return addPatient(call.getArgument(0));
        }).when(createPatient).create(any());
        assertThatThrownBy(() -> patient.seed(data)).hasMessage("simulated outage");
        assertThat(count(patientJdbc)).isZero();
        assertThat(count(orgJdbc)).isEqualTo(30);
        // Organization is already committed; its normal creation ports are never called again.
        clearInvocations(createDepartment, createStaff);
        organization.seedMasters(data);
        verifyNoInteractions(createDepartment, createStaff);
        doAnswer(call -> addPatient(call.getArgument(0))).when(createPatient).create(any());
        runSeed();
        assertThat(patients).hasSize(10);
        assertThat(accounts).hasSize(10);
        assertThat(count(patientJdbc)).isEqualTo(10);
    }

    @Test void adoptionReadsBeyondFirstPage() {
        runSeed();
        List<Staff> real = new ArrayList<>(staff);
        for (int i = 0; i < 101; i++) {
            staff.addFirst(Staff.create("Other Staff", departments.getFirst().getDepartmentId(),
                    JobTitle.ADMINISTRATIVE, null, null, null, null, String.format("888880%06d", i)));
        }
        orgJdbc.update("delete from local_demo_seed_receipt where seed_key like 'staff-%'");
        clearInvocations(createStaff);
        var ids = organization.seedMasters(data);
        verifyNoInteractions(createStaff);
        assertThat(ids.get("staff-01")).isEqualTo(real.getFirst().getStaffId());
    }

    private void runSeed() {
        var ids = organization.seedMasters(data);
        organization.seedAccounts(data, ids, patient.seed(data));
    }

    private PatientDTO addPatient(CreatePatientRequest request) {
        var row = new PatientDTO(UUID.randomUUID(), request.hoTen(), request.ngaySinh(),
                request.gioiTinh(), request.soCmnd(), request.diaChi(), request.soDienThoai(),
                request.email(), request.bhytSo(), Instant.now(), Instant.now());
        patients.add(row);
        return row;
    }

    private static <T> PageResult<T> page(List<T> rows, PageQuery page) {
        int start = Math.min(page.page() * page.size(), rows.size());
        return PageResult.of(rows.subList(start, Math.min(start + page.size(), rows.size())),
                rows.size(), page.page(), page.size());
    }

    private static JdbcTemplate database() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table local_demo_profile_receipt(seed_key varchar(100) primary key,entity_id uuid not null)");
        jdbc.execute("create table local_demo_seed_receipt(seed_key varchar(100) primary key,entity_id uuid not null)");
        jdbc.execute("create alias pg_advisory_xact_lock for 'com.mediflow.core.config.LocalDemoSeedTest.advisoryLock'");
        return jdbc;
    }

    private static TransactionTemplate transactions(JdbcTemplate jdbc) {
        return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    private static int count(JdbcTemplate jdbc) {
        return jdbc.queryForObject("select count(*) from local_demo_seed_receipt", Integer.class);
    }
}
