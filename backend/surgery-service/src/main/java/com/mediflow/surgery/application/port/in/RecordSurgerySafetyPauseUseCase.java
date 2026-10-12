package com.mediflow.surgery.application.port.in;

import com.mediflow.surgery.domain.model.SurgerySafetyPause;

/** Internal boundary only; no role grants policy/evidence/participant authority. */
public interface RecordSurgerySafetyPauseUseCase {
    SurgerySafetyPause record(SurgerySafetyPause proposal);
}
