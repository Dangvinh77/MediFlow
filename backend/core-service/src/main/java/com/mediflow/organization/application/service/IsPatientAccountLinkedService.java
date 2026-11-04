package com.mediflow.organization.application.service;

import com.mediflow.organization.application.port.in.IsPatientAccountLinkedUseCase;
import com.mediflow.organization.application.port.out.AccountRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Transactional(value = "organizationTransactionManager", readOnly = true)
public class IsPatientAccountLinkedService implements IsPatientAccountLinkedUseCase {
    private final AccountRepository accounts;
    public IsPatientAccountLinkedService(AccountRepository accounts) { this.accounts = accounts; }
    @Override public boolean isLinked(UUID patientId) { return accounts.existsByPatientId(patientId); }
}
