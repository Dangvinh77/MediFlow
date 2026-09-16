package com.mediflow.report.infrastructure.persistence.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.report.application.port.out.DrugStatisticRepositoryPort;
import com.mediflow.report.domain.exception.ReportRuleException;
import com.mediflow.report.domain.model.DrugStatistic;
import com.mediflow.report.domain.model.TopMedicineSummary;
import com.mediflow.report.infrastructure.persistence.DrugStatisticJpaEntity;
import com.mediflow.report.infrastructure.persistence.DrugStatisticPersistenceMapper;
import com.mediflow.report.infrastructure.persistence.repository.DrugStatisticJpaRepository;

import lombok.RequiredArgsConstructor;

/** Concurrency-safe persistence adapter for drug statistics and top queries. */
@Component
@RequiredArgsConstructor
public class DrugStatisticPersistenceAdapter implements DrugStatisticRepositoryPort {

    private final DrugStatisticJpaRepository repository;

    @Override
    @Transactional
    public DrugStatistic findOrCreate(UUID drugId, String drugName, LocalDate reportDate, UUID departmentId) {
        DrugStatistic.initialize(drugId, drugName, reportDate, departmentId);
        repository.insertIfAbsent(UUID.randomUUID(), drugId, drugName.trim(), reportDate, departmentId);
        return repository.findForUpdate(drugId, reportDate, departmentId)
                .map(DrugStatisticPersistenceMapper::toDomain)
                .orElseThrow(() -> new IllegalStateException("Drug statistic row disappeared after upsert"));
    }

    @Override
    @Transactional
    public DrugStatistic save(DrugStatistic statistic) {
        DrugStatisticJpaEntity saved = repository.save(DrugStatisticPersistenceMapper.toEntity(statistic));
        return DrugStatisticPersistenceMapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopMedicineSummary> topMedicines(LocalDate fromDate, LocalDate toDate,
                                                  UUID departmentId, int limit) {
        return repository.topMedicines(fromDate, toDate, departmentId, limit).stream()
                .map(row -> {
                    try {
                        return new TopMedicineSummary(row.getDrugId(), row.getDrugName(),
                                Math.toIntExact(row.getTotalQuantity()));
                    } catch (ArithmeticException ex) {
                        throw new ReportRuleException("REPORT_DRUG_QUANTITY_INVALID",
                                "Tổng số lượng thuốc vượt giới hạn");
                    }
                })
                .toList();
    }
}
