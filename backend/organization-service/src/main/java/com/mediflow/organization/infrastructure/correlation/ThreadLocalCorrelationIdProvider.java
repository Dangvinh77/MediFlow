package com.mediflow.organization.infrastructure.correlation;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.organization.application.port.out.CorrelationIdProvider;

/** Binds one correlation id to the thread handling an HTTP request. */
@Component
public class ThreadLocalCorrelationIdProvider implements CorrelationIdProvider {

    private final ThreadLocal<UUID> current = new ThreadLocal<>();

    @Override
    public UUID currentOrCreate() {
        UUID correlationId = current.get();
        if (correlationId == null) {
            correlationId = UUID.randomUUID();
            current.set(correlationId);
        }
        return correlationId;
    }

    public void bind(UUID correlationId) {
        if (correlationId == null) {
            current.remove();
        } else {
            current.set(correlationId);
        }
    }

    public void clear() {
        current.remove();
    }
}
