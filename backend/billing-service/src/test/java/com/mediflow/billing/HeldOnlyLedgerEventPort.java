package com.mediflow.billing;

import java.util.UUID;
import java.util.function.BiConsumer;

import com.mediflow.billing.application.event.LedgerIntegrationEvent;
import com.mediflow.billing.application.port.out.LedgerEventPort;

/**
 * Test helper: adapts a two-arg {@code (accountId, event)} lambda to the full {@link LedgerEventPort}
 * contract. {@code LedgerEventPort} stopped being a single-method functional interface once L2 added
 * {@code appendForOutpatientRequest} (HANDOFF-LOC-OUTPATIENT-DISCOVERY-PUBLICATION.md §4); the
 * non-outpatient tests using this helper only ever exercise {@code appendHeld}-shaped behavior, so
 * both methods delegate to the same lambda.
 */
final class HeldOnlyLedgerEventPort implements LedgerEventPort {
    private final BiConsumer<UUID, LedgerIntegrationEvent> onAppend;

    HeldOnlyLedgerEventPort(BiConsumer<UUID, LedgerIntegrationEvent> onAppend) {
        this.onAppend = onAppend;
    }

    @Override public void appendHeld(UUID accountId, LedgerIntegrationEvent event) {
        onAppend.accept(accountId, event);
    }

    @Override public void appendForOutpatientRequest(UUID accountId, UUID paymentRequestId, LedgerIntegrationEvent event) {
        onAppend.accept(accountId, event);
    }
}
