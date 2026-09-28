package com.mediflow.surgery.domain.model;

/** Source of an audited action; a broker producer is never represented by a fabricated UUID. */
public enum SurgeryActorType {
    HUMAN,
    SYSTEM
}
