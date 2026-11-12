package com.mediflow.clinical.application.port.out;

/** Whether medical-record reads go through the v2 access policy. */
public record RecordAccessPolicyMode(boolean enabled) {}
