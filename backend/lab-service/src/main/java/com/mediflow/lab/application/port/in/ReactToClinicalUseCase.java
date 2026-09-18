package com.mediflow.lab.application.port.in;

import java.util.UUID;

/**
 * Contract for reacting to an explicit Clinical lab order.
 */
public interface ReactToClinicalUseCase {

    void autoCreateFromRecord(UUID recordId, UUID patientId, UUID departmentId, String labType);

}
