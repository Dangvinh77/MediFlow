package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.request.AcceptOutpatientCoverageRequest;
import com.mediflow.report.application.exception.OutpatientCoverageConflictException;
import com.mediflow.report.application.exception.ReportPeriodValidationException;
import com.mediflow.report.application.port.in.AcceptOutpatientCoverageUseCase;
import com.mediflow.report.application.port.out.OutpatientCoverageAcceptancePort;
import com.mediflow.report.application.port.out.OutpatientCoverageEvidencePort;
import com.mediflow.report.application.port.out.OutpatientCoverageEvidencePort.Snapshot;
import com.mediflow.report.domain.model.OutpatientCoverage;
import com.mediflow.report.application.dto.response.OutpatientCoverageDTO;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

public class OutpatientCoverageAcceptanceService implements AcceptOutpatientCoverageUseCase {
    private final OutpatientCoverageEvidencePort evidence;
    private final OutpatientCoverageAcceptancePort store;
    private final ZoneId zone;
    private final Clock clock;

    public OutpatientCoverageAcceptanceService(OutpatientCoverageEvidencePort evidence,
            OutpatientCoverageAcceptancePort store, ZoneId zone, Clock clock) {
        this.evidence = evidence; this.store = store; this.zone = zone; this.clock = clock;
    }

    @Override
    @Transactional(transactionManager="reportTransactionManager", readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Snapshot preview(LocalDate from, LocalDate to, UUID department) {
        validatePeriod(from, to);
        return evidence.inspect(from, to, department, zone.getId(), "VND");
    }

    @Override
    @Transactional(transactionManager="reportTransactionManager", isolation=Isolation.READ_COMMITTED)
    public OutpatientCoverageDTO accept(AcceptOutpatientCoverageRequest request, UUID actor) {
        if (request == null || actor == null) throw new ReportPeriodValidationException();
        validatePeriod(request.fromDate(), request.toDate());
        Instant now = clock.instant();
        var references = new TreeMap<String,String>();
        if (request.requestId() == null || !zone.getId().equals(request.zoneId()) || !"VND".equals(request.currency())
                || request.sources() == null || !request.sources().keySet().equals(OutpatientCoverage.REQUIRED_SOURCES))
            throw new ReportPeriodValidationException();
        request.sources().forEach((source, proof) -> {
            if (proof == null || proof.reference() == null || proof.reference().isBlank() || proof.reference().length() > 255
                    || !hash(proof.exportSha256()) || !hash(proof.localFingerprint()) || proof.rowCount() < 0)
                throw new ReportPeriodValidationException();
            references.put(source, proof.reference());
        });
        // Validate every temporal/scope field before touching persistence, including complete final day.
        OutpatientCoverage candidate;
        try {
            candidate = new OutpatientCoverage(UUID.randomUUID(), request.fromDate(), request.toDate(), request.departmentId(),
                    request.zoneId(), request.currency(), references, request.sourceObservedAt(), request.reconciledAt(), now,
                    actor, request.acceptanceReference(), request.expectedFingerprint());
        } catch (IllegalArgumentException | DateTimeException invalid) {
            throw new ReportPeriodValidationException();
        }
        store.lock();
        var prior = store.find(request.requestId());
        if (prior.isPresent()) {
            if (!prior.get().request().equals(request) || !prior.get().actor().equals(actor))
                throw new OutpatientCoverageConflictException();
            // A retry returns the original audit receipt; it never revalidates/reaccepts a drifted window.
            return OutpatientCoverageDTO.from(prior.get().coverage());
        }
        var snapshot = evidence.inspect(request.fromDate(), request.toDate(), request.departmentId(), zone.getId(), "VND");
        if (snapshot.pendingOrInvalid() || !snapshot.fingerprint().equals(request.expectedFingerprint())
                || !snapshot.sources().keySet().equals(OutpatientCoverage.REQUIRED_SOURCES))
            throw new OutpatientCoverageConflictException();
        for (var source : OutpatientCoverage.REQUIRED_SOURCES) {
            var expected = request.sources().get(source);
            var actual = snapshot.sources().get(source);
            if (actual.rowCount() != expected.rowCount() || !actual.fingerprint().equals(expected.localFingerprint()))
                throw new OutpatientCoverageConflictException();
        }
        store.save(request, actor, candidate);
        return OutpatientCoverageDTO.from(candidate);
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.getYear() < 2000 || to.getYear() > 2100
                || ChronoUnit.DAYS.between(from, to) >= 366) throw new ReportPeriodValidationException();
    }
    private boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
}
