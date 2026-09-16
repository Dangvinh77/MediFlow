package com.mediflow.report.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.port.out.MonthlyRevenueReportRepositoryPort;
import com.mediflow.report.domain.model.MonthlyRevenueReport;
import com.mediflow.report.infrastructure.persistence.MonthlyRevenueReportJpaEntity;
import com.mediflow.report.infrastructure.persistence.MonthlyRevenueReportPersistenceMapper;
import com.mediflow.report.infrastructure.persistence.repository.MonthlyRevenueReportJpaRepository;

import lombok.RequiredArgsConstructor;

/** Concurrency-safe persistence adapter for monthly revenue rows. */
@Component
@RequiredArgsConstructor
public class MonthlyRevenueReportPersistenceAdapter implements MonthlyRevenueReportRepositoryPort {

    private final MonthlyRevenueReportJpaRepository repository;

    @Override
    @Transactional
    public MonthlyRevenueReport findOrCreate(int year, int month, UUID departmentId) {
        MonthlyRevenueReport.initialize(month, year, departmentId);
        repository.insertIfAbsent(UUID.randomUUID(), month, year, departmentId);
        return repository.findForUpdate(month, year, departmentId)
                .map(MonthlyRevenueReportPersistenceMapper::toDomain)
                .orElseThrow(() -> new IllegalStateException("Monthly report row disappeared after upsert"));
    }

    @Override
    @Transactional
    public MonthlyRevenueReport save(MonthlyRevenueReport report) {
        MonthlyRevenueReportJpaEntity saved = repository.save(MonthlyRevenueReportPersistenceMapper.toEntity(report));
        return MonthlyRevenueReportPersistenceMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MonthlyRevenueReport> find(int year, int month, UUID departmentId) {
        return repository.findByScope(month, year, departmentId)
                .map(MonthlyRevenueReportPersistenceMapper::toDomain);
    }
}
