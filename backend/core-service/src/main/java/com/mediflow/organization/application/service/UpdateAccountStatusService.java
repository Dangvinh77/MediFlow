package com.mediflow.organization.application.service;

import java.util.UUID;

import com.mediflow.organization.application.port.in.UpdateAccountStatusUseCase;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.application.port.out.StaffRepository;
import com.mediflow.organization.domain.exception.InvalidAccountException;
import com.mediflow.organization.domain.exception.AccountNotFoundException;
import com.mediflow.organization.domain.model.Account;
import org.springframework.transaction.annotation.Transactional;

@Transactional("organizationTransactionManager")
public class UpdateAccountStatusService implements UpdateAccountStatusUseCase {

    private final AccountRepository accountRepository;
    private final StaffRepository staffRepository;

    public UpdateAccountStatusService(AccountRepository accountRepository, StaffRepository staffRepository) {
        this.accountRepository = accountRepository;
        this.staffRepository = staffRepository;
    }

    @Override
    public Account execute(UUID accountId, boolean active) {
        return execute(accountId, active, false);
    }

    @Override
    public Account execute(UUID accountId, boolean active, boolean replaceActiveAccount) {
        // Owner lock must precede row locks, including when switching two accounts concurrently.
        Account snapshot = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        accountRepository.lockOwner(snapshot.getStaffId(), snapshot.getPatientId());
        Account account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (active && account.getStaffId() != null) {
            var owner = staffRepository.findById(account.getStaffId());
            if (owner.isEmpty() || !owner.get().isActive()) {
                throw new InvalidAccountException("Không thể kích hoạt tài khoản của nhân viên đã ngừng làm việc.");
            }
        }
        if (active) AccountOwnerPolicy.check(accountRepository, account, replaceActiveAccount);
        boolean changed = active != account.isActive();
        if (active && !account.isActive()) {
            account.activate();
        } else if (!active && account.isActive()) {
            account.deactivate();
        }

        Account saved = accountRepository.save(account);
        if (changed) accountRepository.incrementCredentialVersion(accountId);
        return saved;
    }
}
