package com.mediflow.report.infrastructure.persistence.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.port.out.DailyVisitReportRepositoryPort;
import com.mediflow.report.domain.model.DailyVisitReport;
import com.mediflow.report.infrastructure.persistence.DailyVisitReportJpaEntity;
import com.mediflow.report.infrastructure.persistence.DailyVisitReportPersistenceMapper;
import com.mediflow.report.infrastructure.persistence.repository.DailyVisitReportJpaRepository;

import lombok.RequiredArgsConstructor;

/** Concurrency-safe persistence adapter for daily report rows. */
@Component
@RequiredArgsConstructor
public class DailyVisitReportPersistenceAdapter implements DailyVisitReportRepositoryPort {

    private final DailyVisitReportJpaRepository repository;

    @Override
    @Transactional
    public DailyVisitReport findOrCreate(LocalDate reportDate, UUID departmentId) {
        DailyVisitReport.initialize(reportDate, departmentId);
        repository.insertIfAbsent(UUID.randomUUID(), reportDate, departmentId);
        return repository.findForUpdate(reportDate, departmentId)
                .map(DailyVisitReportPersistenceMapper::toDomain)
                .orElseThrow(() -> new IllegalStateException("Daily report row disappeared after upsert"));
    }

    @Override
    @Transactional
    public DailyVisitReport save(DailyVisitReport report) {
        DailyVisitReportJpaEntity saved = repository.save(DailyVisitReportPersistenceMapper.toEntity(report));
        return DailyVisitReportPersistenceMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DailyVisitReport> find(LocalDate reportDate, UUID departmentId) {
        return repository.findByScope(reportDate, departmentId).map(DailyVisitReportPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyVisitReport> findRange(LocalDate fromDate, LocalDate toDate, UUID departmentId) {
        return repository.findRangeByScope(fromDate, toDate, departmentId).stream()
                .map(DailyVisitReportPersistenceMapper::toDomain)
                .toList();
    }
}
