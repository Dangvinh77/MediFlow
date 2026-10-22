package com.mediflow.organization.application.service;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.domain.model.Account;

/** Call inside the same Organization transaction as the final account save. */
final class AccountOwnerPolicy {
    private AccountOwnerPolicy() {}

    static void check(AccountRepository accounts, Account target, boolean replaceActiveAccount) {
        if (target.getStaffId() == null && target.getPatientId() == null) return;
        accounts.lockOwner(target.getStaffId(), target.getPatientId());
        var others = accounts.findActiveByOwner(target.getStaffId(), target.getPatientId()).stream()
                .filter(account -> !account.getAccountId().equals(target.getAccountId())).toList();
        if (!others.isEmpty() && !replaceActiveAccount) {
            throw new DuplicateResourceException("ACCOUNT_OWNER_ACTIVE",
                    "Người này đã có tài khoản đang active: "
                            + String.join(", ", others.stream().map(Account::getUsername).toList())
                            + ". Nếu tiếp tục, các tài khoản này sẽ bị inactive.");
        }
        for (Account previous : others) {
            previous.deactivate();
            accounts.save(previous);
            accounts.incrementCredentialVersion(previous.getAccountId());
        }
    }
}
