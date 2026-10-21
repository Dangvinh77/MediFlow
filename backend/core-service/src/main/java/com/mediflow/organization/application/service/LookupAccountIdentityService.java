package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.AccountIdentityLookupDTO;
import com.mediflow.organization.application.port.in.LookupAccountIdentityUseCase;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.model.Account;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Transactional(readOnly = true)
public class LookupAccountIdentityService implements LookupAccountIdentityUseCase {
    private final AccountRepository accounts;
    private final StaffRepository staff;

    public LookupAccountIdentityService(AccountRepository accounts, StaffRepository staff) {
        this.accounts = accounts;
        this.staff = staff;
    }

    @Override
    public AccountIdentityLookupDTO lookup(UUID accountId) {
        return accounts.findById(accountId)
                .map(this::toProjection)
                .orElseGet(AccountIdentityLookupDTO::missing);
    }

    private AccountIdentityLookupDTO toProjection(Account account) {
        UUID departmentId = account.getStaffId() == null
                ? null
                : staff.findById(account.getStaffId()).map(s -> s.getDepartmentId()).orElse(null);
        return new AccountIdentityLookupDTO(
                true,
                account.isActive(),
                account.getAccountId(),
                account.getRole().name(),
                account.getStaffId(),
                departmentId,
                account.getPatientId());
    }
}
