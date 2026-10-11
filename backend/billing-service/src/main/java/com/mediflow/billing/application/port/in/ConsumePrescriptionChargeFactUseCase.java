package com.mediflow.billing.application.port.in;

/** Version-aware wire intake seam for the existing compatibility dispatcher. */
public interface ConsumePrescriptionChargeFactUseCase {
    /** False only for a flat compatibility fact; invalid versioned input must throw. */
    boolean receiveIfVersioned(String routingKey, byte[] body);
}
