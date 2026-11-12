package com.mediflow.billing.application.service;

import com.mediflow.billing.application.dto.command.PrescriptionRevisionCommand;
import com.mediflow.billing.application.dto.response.PrescriptionRevisionResult;
import com.mediflow.billing.application.port.in.RevisePrescriptionChargeUseCase;
import com.mediflow.billing.application.port.out.PrescriptionRevisionFingerprintPort;
import com.mediflow.billing.application.port.out.PrescriptionRevisionRepositoryPort;
import com.mediflow.billing.application.port.out.PrescriptionPricePolicyPort;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class PrescriptionRevisionService implements RevisePrescriptionChargeUseCase {
    private final PrescriptionRevisionRepositoryPort repository;
    private final PrescriptionRevisionFingerprintPort fingerprints;
    private final PrescriptionPricePolicyPort prices;
    private final Clock clock;

    public PrescriptionRevisionService(PrescriptionRevisionRepositoryPort repository,
            PrescriptionRevisionFingerprintPort fingerprints, PrescriptionPricePolicyPort prices, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.fingerprints = Objects.requireNonNull(fingerprints);
        this.prices = Objects.requireNonNull(prices);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override public PrescriptionRevisionResult prepare(PrescriptionRevisionCommand command) {
        requireApiCommand(command);
        prices.requireAllowed(command.priceCode());
        String payloadFingerprint = fingerprints.payloadFingerprint(command);
        return result(repository.prepare(command, payloadFingerprint, clock.instant()));
    }

    @Override public PrescriptionRevisionResult commit(PrescriptionRevisionCommand command) {
        requireApiCommand(command);
        prices.requireAllowed(command.priceCode());
        String payloadFingerprint = fingerprints.payloadFingerprint(command);
        return result(repository.commit(command, payloadFingerprint, fingerprints.snapshotFingerprint(command),
                null, clock.instant()));
    }

    @Override public PrescriptionRevisionResult abort(PrescriptionRevisionCommand command) {
        requireApiCommand(command);
        prices.requireAllowed(command.priceCode());
        return result(repository.abort(command, fingerprints.payloadFingerprint(command), clock.instant()));
    }

    @Override public PrescriptionRevisionResult applyRevisedEvent(PrescriptionRevisionCommand command) {
        if (command == null || command.eventId() == null || command.revisedAt() == null) {
            throw new IllegalArgumentException("A complete prescription.revised event is required");
        }
        prices.requireAllowed(command.priceCode());
        String payloadFingerprint = fingerprints.payloadFingerprint(command);
        return result(repository.commit(command, payloadFingerprint, fingerprints.snapshotFingerprint(command),
                command.eventId(), command.revisedAt()));
    }

    private static void requireApiCommand(PrescriptionRevisionCommand command) {
        if (command == null || command.eventId() != null || command.revisedAt() != null) {
            throw new IllegalArgumentException("A valid direct prescription revision command is required");
        }
    }

    private static PrescriptionRevisionResult result(PrescriptionRevisionRepositoryPort.OperationReceipt receipt) {
        return new PrescriptionRevisionResult(receipt.prescriptionId(), receipt.operationId(), receipt.revision(),
                receipt.status(), receipt.payloadFingerprint());
    }
}
