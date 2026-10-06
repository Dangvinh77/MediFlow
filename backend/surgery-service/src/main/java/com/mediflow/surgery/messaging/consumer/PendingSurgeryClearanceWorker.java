package com.mediflow.surgery.messaging.consumer;

import com.mediflow.surgery.application.port.in.QueryPendingSurgeryClearancesUseCase;
import com.mediflow.surgery.application.port.in.ReactToSurgeryClearanceUseCase;
import com.mediflow.surgery.application.port.in.RecordSurgeryClearanceRetryUseCase;
import com.mediflow.surgery.application.port.out.SurgeryClearanceWirePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Stateless recovery across restarts; the inbox and case locks fence concurrent instances. */
public class PendingSurgeryClearanceWorker {
    private static final Logger LOG = LoggerFactory.getLogger(PendingSurgeryClearanceWorker.class);
    private final QueryPendingSurgeryClearancesUseCase pending;
    private final ReactToSurgeryClearanceUseCase clearance;
    private final SurgeryClearanceWirePort decoder;
    private final RecordSurgeryClearanceRetryUseCase retry;
    public PendingSurgeryClearanceWorker(QueryPendingSurgeryClearancesUseCase pending,
            ReactToSurgeryClearanceUseCase clearance, SurgeryClearanceWirePort decoder,
            RecordSurgeryClearanceRetryUseCase retry) {
        this.pending = pending; this.clearance = clearance; this.decoder = decoder; this.retry = retry;
    }
    @Scheduled(fixedDelayString = "${mediflow.surgery.messaging.consumers.pending-poll-ms:5000}")
    public void poll() {
        for (var event : pending.due(20)) {
            try {
                var command = decoder.decode(event.eventType(),event.payload(),event.receivedAt());
                clearance.receive(command);
            } catch (RuntimeException failure) {
                try { retry.deferFailure(event); }
                catch (RuntimeException unavailable) {
                    LOG.warn("Pending Surgery clearance backoff could not be recorded: eventId={}",event.eventId());
                }
                // No payload, patient data or exception text. One unavailable target does not starve the batch.
                LOG.warn("Pending Surgery clearance retry failed: eventId={}",event.eventId());
            }
        }
    }
}
