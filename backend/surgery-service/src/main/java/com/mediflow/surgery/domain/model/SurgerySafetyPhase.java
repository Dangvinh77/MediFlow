package com.mediflow.surgery.domain.model;

/** Observed safety pauses are separate from pre-op readiness and actual procedure timestamps. */
public enum SurgerySafetyPhase {
    BEFORE_ANAESTHESIA,
    BEFORE_INCISION,
    BEFORE_OR_EXIT;

    public SurgerySafetyPhase previous() {
        return switch (this) {
            case BEFORE_ANAESTHESIA -> null;
            case BEFORE_INCISION -> BEFORE_ANAESTHESIA;
            case BEFORE_OR_EXIT -> BEFORE_INCISION;
        };
    }
}
