package com.mediflow.clinical.infrastructure.correlation;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mediflow.clinical.application.port.out.CorrelationIdProvider;

/**
 * Binds a correlation id to the thread handling one HTTP request.
 *
 * <p>The servlet filter owns the lifecycle and always clears this holder. The application layer
 * sees only {@link CorrelationIdProvider}.</p>
 */
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
            return;
        }
        current.set(correlationId);
    }

    public UUID peek() {
        return current.get();
    }

    public void clear() {
        current.remove();
    }
}
