package com.mediflow.report.application.dto.response;

import java.util.UUID;

/** Restoration creates an isolated BUILDING generation; it never approves historical coverage. */
public record FinancialArchiveRestoreDTO(UUID generationId, UUID originGenerationId, String archiveFingerprint) { }
