package com.mediflow.organization.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediflow.common.api.PageQuery;
import com.mediflow.organization.application.port.in.*;
import com.mediflow.organization.domain.model.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Organization-owned seed transactions; invokes the normal use cases/outbox paths. */
public class OrganizationDemoSeeder {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final GetDepartmentUseCase departments;
    private final CreateDepartmentUseCase createDepartment;
    private final GetStaffUseCase staff;
    private final CreateStaffUseCase createStaff;
    private final ManageAccountsUseCase accounts;
    private final CreateAccountUseCase createAccount;

    public OrganizationDemoSeeder(JdbcTemplate jdbc, TransactionTemplate transaction,
            GetDepartmentUseCase departments, CreateDepartmentUseCase createDepartment,
            GetStaffUseCase staff, CreateStaffUseCase createStaff,
            ManageAccountsUseCase accounts, CreateAccountUseCase createAccount) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.departments = departments;
        this.createDepartment = createDepartment;
        this.staff = staff;
        this.createStaff = createStaff;
        this.accounts = accounts;
        this.createAccount = createAccount;
    }

    public Map<String, UUID> seedMasters(JsonNode data) {
        return transaction.execute(status -> {
            lock();
            Map<String, UUID> ids = new HashMap<>();
            List<Department> existingDepartments = departments.getAllDepartments();
            for (JsonNode row : data.required("departments")) {
                UUID id = once(row.required("key").asText(), () -> existingDepartments.stream()
                        .filter(d -> d.getAbbreviation().equals(row.required("abbreviation").asText()))
                        .findFirst().map(Department::getDepartmentId)
                        .orElseGet(() -> createDepartment.execute(text(row, "departmentName"),
                                text(row, "abbreviation"), DepartmentType.valueOf(text(row, "departmentType")),
                                text(row, "location")).getDepartmentId()));
                ids.put(text(row, "key"), id);
            }
            List<Staff> existingStaff = allStaff();
            for (JsonNode row : data.required("staff")) {
                UUID id = once(text(row, "key"), () -> existingStaff.stream()
                        .filter(s -> Objects.equals(s.getIdentityNumber(), text(row, "identityNumber")))
                        .findFirst().map(Staff::getStaffId)
                        .orElseGet(() -> createStaff.execute(text(row, "fullName"),
                                Objects.requireNonNull(ids.get(text(row, "departmentKey"))),
                                JobTitle.valueOf(text(row, "jobTitle")), text(row, "specialization"),
                                text(row, "licenseNumber"), text(row, "phoneNumber"), text(row, "email"),
                                text(row, "identityNumber")).getStaffId()));
                ids.put(text(row, "key"), id);
            }
            return ids;
        });
    }

    public void seedAccounts(JsonNode data, Map<String, UUID> staffIds, Map<String, UUID> patientIds) {
        transaction.executeWithoutResult(status -> {
            lock();
            List<Account> existing = allAccounts();
            for (JsonNode row : data.required("accounts")) {
                once(text(row, "key"), () -> {
                    UUID staffId = text(row, "staffKey") == null ? null
                            : Objects.requireNonNull(staffIds.get(text(row, "staffKey")));
                    UUID patientId = text(row, "patientKey") == null ? null
                            : Objects.requireNonNull(patientIds.get(text(row, "patientKey")));
                    Role role = Role.valueOf(text(row, "role"));
                    var match = existing.stream().filter(a -> a.getUsername().equals(text(row, "username")))
                            .findFirst();
                    if (match.isPresent()) {
                        Account account = match.get();
                        if (account.getRole() != role || !Objects.equals(account.getStaffId(), staffId)
                                || !Objects.equals(account.getPatientId(), patientId)) {
                            throw new IllegalStateException("Demo account identity conflict; existing account left unchanged");
                        }
                        return account.getAccountId();
                    }
                    return createAccount.execute(text(row, "username"), text(row, "password"),
                            staffId, patientId, role).getAccountId();
                });
            }
        });
    }

    private List<Staff> allStaff() {
        List<Staff> result = new ArrayList<>();
        for (int page = 0; ; page++) {
            var batch = staff.search(null, null, PageQuery.of(page, 100));
            result.addAll(batch.content());
            if (page + 1 >= batch.totalPages()) return result;
        }
    }

    private List<Account> allAccounts() {
        List<Account> result = new ArrayList<>();
        for (int page = 0; ; page++) {
            var batch = accounts.search(null, null, null, PageQuery.of(page, 100));
            result.addAll(batch.content());
            if (page + 1 >= batch.totalPages()) return result;
        }
    }

    private void lock() {
        jdbc.execute("select pg_advisory_xact_lock(78324512)");
    }

    private UUID once(String key, Supplier<UUID> createOrAdopt) {
        var saved = jdbc.query("select entity_id from local_demo_seed_receipt where seed_key=?",
                (rs, index) -> rs.getObject(1, UUID.class), key);
        if (!saved.isEmpty()) return saved.getFirst();
        UUID id = createOrAdopt.get();
        jdbc.update("insert into local_demo_seed_receipt(seed_key,entity_id) values (?,?)", key, id);
        return id;
    }

    private static String text(JsonNode row, String field) {
        return row.required(field).isNull() ? null : row.required(field).asText();
    }
}
