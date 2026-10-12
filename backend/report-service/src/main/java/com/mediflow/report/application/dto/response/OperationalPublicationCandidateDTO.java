package com.mediflow.report.application.dto.response;

/** Proof of the finite frozen input set. Does not attest historical or upstream coverage. */
public record OperationalPublicationCandidateDTO(OperationalReplayProgress progress, String manifestFingerprint) { }
