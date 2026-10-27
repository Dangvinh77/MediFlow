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

    @Override public boolean existsByPatientId(UUID patientId) {
        return jpaRepository.existsByPatientId(patientId);
    }

    private final AccountJpaRepository jpaRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext(unitName = "organization")
    private jakarta.persistence.EntityManager entityManager;

    public AccountRepositoryAdapter(AccountJpaRepository jpaRepository,
            @org.springframework.beans.factory.annotation.Qualifier("organizationDataSource") javax.sql.DataSource source) {
        this.jpaRepository = jpaRepository;
        this.jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
    }

    @Override
    public void lockOwner(UUID staffId, UUID patientId) {
        if (staffId == null && patientId == null) return;
        String owner = staffId != null ? "staff:" + staffId : "patient:" + patientId;
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) result -> { result.next(); return null; },
                "account-owner:" + owner);
    }

    @Override
    public java.util.List<Account> findActiveByOwner(UUID staffId, UUID patientId) {
        return jpaRepository.findActiveByOwner(staffId, patientId).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<Account> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(entity -> {
            // A status command may have read its owner before waiting for the owner lock.
            // Refresh the managed snapshot after acquiring the row lock.
            entityManager.refresh(entity, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            return toDomain(entity);
        });
    }

    @Override
    public Optional<Account> findByUsernameForUpdate(String username) {
        return jpaRepository.findByUsernameForUpdate(username).map(this::toDomain);
    }

    @Override
    public long credentialVersion(UUID id) { return jpaRepository.credentialVersion(id); }

    @Override
    public void incrementCredentialVersion(UUID id) {
        if (jpaRepository.incrementCredentialVersion(id) != 1) throw new IllegalStateException("Account disappeared during credential change");
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
        // JDBC command receipts reference this row before the transaction commits.
        return toDomain(jpaRepository.saveAndFlush(toEntity(account)));
    }

    @Override
    public com.mediflow.common.api.PageResult<Account> search(String keyword,
            com.mediflow.organization.domain.model.Role role, Boolean active,
            com.mediflow.common.api.PageQuery query) {
        org.springframework.data.jpa.domain.Specification<AccountEntity> filter = (root, criteria, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (keyword != null && !keyword.isBlank()) {
                String escaped = keyword.trim().toLowerCase(java.util.Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_");
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + escaped + "%", '!'));
            }
            if (role != null) predicates.add(cb.equal(root.get("role"), role));
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var result = jpaRepository.findAll(filter, org.springframework.data.domain.PageRequest.of(
                query.page(), query.size(), org.springframework.data.domain.Sort.by("username").and(
                        org.springframework.data.domain.Sort.by("accountId"))));
        return com.mediflow.common.api.PageResult.of(result.getContent().stream().map(this::toDomain).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    private Account toDomain(AccountEntity entity) {
        Account account = new Account(
                entity.getAccountId(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getStaffId(),
                entity.getPatientId(),
                entity.getRole(),
                entity.isActive(),
                entity.getLastLoginAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
        if (entity.isMustChangePassword()) account.requirePasswordChange();
        return account;
    }

    private AccountEntity toEntity(Account account) {
        AccountEntity entity = new AccountEntity(
                account.getAccountId(),
                account.getUsername(),
                account.getPasswordHash(),
                account.getStaffId(),
                account.getPatientId(),
                account.getRole(),
                account.isActive(),
                account.getLastLoginAt(),
                account.getCreatedAt(),
                account.getUpdatedAt());
        if (account.isMustChangePassword()) entity.requirePasswordChange();
        return entity;
    }
}
