package com.mediflow.patient.infrastructure.correlation;

import com.mediflow.patient.application.port.out.CorrelationIdProvider;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ThreadLocalCorrelationIdProvider implements CorrelationIdProvider {
    private final ThreadLocal<UUID> current = new ThreadLocal<>();

    @Override
    public UUID currentOrCreate() {
        UUID value = current.get();
        if (value == null) {
            value = UUID.randomUUID();
            current.set(value);
        }
        return value;
    }

    public void bind(UUID value) { current.set(value); }
    public void clear() { current.remove(); }
}
