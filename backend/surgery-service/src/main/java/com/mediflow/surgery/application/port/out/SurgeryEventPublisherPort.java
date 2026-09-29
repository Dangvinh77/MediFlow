package com.mediflow.surgery.application.port.out;

import java.util.UUID;

/** Transport boundary for already-serialized Surgery outbox bytes. */
public interface SurgeryEventPublisherPort {

    void publish(OutgoingMessage message);

    record OutgoingMessage(
            UUID eventId,
            String eventType,
            int eventVersion,
            String correlationId,
            byte[] payload) {
        public OutgoingMessage {
            if (eventId == null || eventType == null || eventType.isBlank()
                    || eventVersion < 1 || correlationId == null || correlationId.isBlank()
                    || payload == null || payload.length == 0) {
                throw new IllegalArgumentException("Surgery message identity/payload is invalid");
            }
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }
}
