package com.mediflow.surgery.infrastructure.messaging;

/** Signals a transient publish failure or a mandatory-return from RabbitMQ. */
public class SurgeryEventPublishException extends RuntimeException {

    private final boolean returned;

    public SurgeryEventPublishException(String message, boolean returned) {
        super(message);
        this.returned = returned;
    }

    public SurgeryEventPublishException(String message, Throwable cause) {
        super(message, cause);
        this.returned = false;
    }

    public boolean returned() {
        return returned;
    }
}
