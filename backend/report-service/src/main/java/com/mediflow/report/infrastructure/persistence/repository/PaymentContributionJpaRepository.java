package com.mediflow.report.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mediflow.report.infrastructure.persistence.PaymentContributionJpaEntity;

/** Repository for invoice-keyed contribution state. */
public interface PaymentContributionJpaRepository extends JpaRepository<PaymentContributionJpaEntity, UUID> {}
