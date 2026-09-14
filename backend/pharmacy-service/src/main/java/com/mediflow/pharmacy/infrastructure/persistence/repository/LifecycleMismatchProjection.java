package com.mediflow.pharmacy.infrastructure.persistence.repository;

import java.util.UUID;

/** Native-query projection used only to map reconciliation findings at the adapter boundary. */
public interface LifecycleMismatchProjection {

    /** @return affected prescription id */
    UUID getPrescriptionId();

    /** @return stable anomaly code */
    String getAnomalyType();

    /** @return short operational detail, never a full payload or patient data */
    String getDetails();
}
