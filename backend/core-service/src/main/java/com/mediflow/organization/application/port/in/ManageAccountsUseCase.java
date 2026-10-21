package com.mediflow.organization.application.port.in;

import java.util.UUID;
import com.mediflow.common.api.PageQuery;
import com.mediflow.common.api.PageResult;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.domain.model.Role;

public interface ManageAccountsUseCase {
    PageResult<Account> search(String keyword, Role role, Boolean active, PageQuery page);
    void changePassword(UUID accountId, String currentPassword, String newPassword);
    void resetPassword(UUID accountId, String newPassword);
}
