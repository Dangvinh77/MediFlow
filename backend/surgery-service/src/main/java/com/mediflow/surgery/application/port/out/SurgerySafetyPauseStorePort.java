package com.mediflow.surgery.application.port.out;

import com.mediflow.surgery.domain.model.SurgerySafetyPause;

/** Caller must authorize evidence and supply a transaction; recording never implies START/COMPLETE. */
public interface SurgerySafetyPauseStorePort {
    SurgerySafetyPause append(SurgerySafetyPause pause);
}
