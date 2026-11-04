package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.ProfileAccountLookupDTO;
import com.mediflow.organization.application.port.in.LookupProfileAccountUseCase;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.PatientAccountIdentityPort;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.organization.domain.exception.StaffNotFoundException;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;
import com.mediflow.organization.domain.model.Staff;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Transactional(value = "organizationTransactionManager", readOnly = true)
public class LookupProfileAccountService implements LookupProfileAccountUseCase {
    private final AccountRepository accounts;
    private final StaffRepository staff;
    private final PatientAccountIdentityPort patients;

    public LookupProfileAccountService(AccountRepository accounts, StaffRepository staff,
            PatientAccountIdentityPort patients) {
        this.accounts = accounts;
        this.staff = staff;
        this.patients = patients;
    }

    @Override
    public ProfileAccountLookupDTO staff(UUID staffId) {
        Staff owner = staff.findById(staffId).orElseThrow(() -> new StaffNotFoundException(staffId));
        Account account = representative(accounts.findByOwner(staffId, null));
        if (account != null && (!Objects.equals(account.getStaffId(), staffId)
                || account.getPatientId() != null || account.getRole() == Role.PATIENT
                || account.getRole() == Role.SYSTEM)) {
            throw new InvalidAccountException("Linked account does not belong to the staff profile");
        }
        return result(account, owner.getEmail());
    }

    @Override
    public ProfileAccountLookupDTO patient(UUID patientId) {
        String email = patients.profileEmail(patientId);
        Account account = representative(accounts.findByOwner(null, patientId));
        if (account != null && (!Objects.equals(account.getPatientId(), patientId)
                || account.getStaffId() != null || account.getRole() != Role.PATIENT)) {
            throw new InvalidAccountException("Linked account does not belong to the patient profile");
        }
        return result(account, email);
    }

    private static ProfileAccountLookupDTO result(Account account, String profileEmail) {
        boolean pending = account != null && !sameEmail(profileEmail, account.getUsername());
        return new ProfileAccountLookupDTO(account, profileEmail, pending);
    }

    private static boolean sameEmail(String profileEmail, String username) {
        return profileEmail != null && username != null
                && profileEmail.trim().equalsIgnoreCase(username.trim());
    }

    private static Account representative(List<Account> linkedAccounts) {
        return linkedAccounts.stream()
                .sorted(Comparator.comparing(Account::isActive).reversed()
                        .thenComparing(Account::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Account::getAccountId, Comparator.reverseOrder()))
                .findFirst().orElse(null);
    }
}
