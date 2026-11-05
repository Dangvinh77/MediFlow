package com.mediflow.patient.application.port.out;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Coordinates Patient email-bearing profile writes with account issuance in Core's co-hosted
 * Organization context. Implementations hold the Organization owner lock until this action
 * returns; any Patient write inside the action keeps its own transaction manager.
 */
public interface PatientAccountOwnerLockPort {
    <T> T withOwnerLock(UUID patientId, Supplier<T> action);
}
