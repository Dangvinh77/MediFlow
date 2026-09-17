package com.mediflow.organization.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.organization.application.port.out.AccountRepository;
import com.mediflow.organization.domain.model.Account;
import com.mediflow.organization.infrastructure.persistence.entity.AccountEntity;
import com.mediflow.organization.infrastructure.persistence.repository.AccountJpaRepository;

@Component
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpaRepository;

    public AccountRepositoryAdapter(AccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        return jpaRepository.findById(accountId).map(this::toDomain);
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(this::toDomain);
    }

    @Override
    public Account save(Account account) {
        return toDomain(jpaRepository.save(toEntity(account)));
    }

    private Account toDomain(AccountEntity entity) {
        return new Account(
                entity.getAccountId(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getStaffId(),
                entity.getRole(),
                entity.isActive(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private AccountEntity toEntity(Account account) {
        return new AccountEntity(
                account.getAccountId(),
                account.getUsername(),
                account.getPasswordHash(),
                account.getStaffId(),
                account.getRole(),
                account.isActive(),
                account.getLastLoginAt(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
