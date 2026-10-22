package com.mediflow.organization.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.organization.infrastructure.persistence.entity.AccountEntity;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<AccountEntity> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AccountEntity a where a.active = true and "
            + "((:staffId is not null and a.staffId = :staffId) or (:patientId is not null and a.patientId = :patientId)) order by a.accountId")
    java.util.List<AccountEntity> findActiveByOwner(
            @org.springframework.data.repository.query.Param("staffId") UUID staffId,
            @org.springframework.data.repository.query.Param("patientId") UUID patientId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AccountEntity a where a.accountId = :id")
    Optional<AccountEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AccountEntity a where a.username = :username")
    Optional<AccountEntity> findByUsernameForUpdate(@org.springframework.data.repository.query.Param("username") String username);

    @org.springframework.data.jpa.repository.Query(value = "select credential_version from account where account_id = :id", nativeQuery = true)
    long credentialVersion(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = "update account set credential_version = credential_version + 1 where account_id = :id", nativeQuery = true)
    int incrementCredentialVersion(@org.springframework.data.repository.query.Param("id") UUID id);

    boolean existsByUsername(String username);

    Optional<AccountEntity> findByUsername(String username);
}
