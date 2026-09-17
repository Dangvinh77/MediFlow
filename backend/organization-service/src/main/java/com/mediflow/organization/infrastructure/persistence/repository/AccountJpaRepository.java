package com.mediflow.organization.infrastructure.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.organization.infrastructure.persistence.entity.AccountEntity;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {

    boolean existsByUsername(String username);

    Optional<AccountEntity> findByUsername(String username);
}
