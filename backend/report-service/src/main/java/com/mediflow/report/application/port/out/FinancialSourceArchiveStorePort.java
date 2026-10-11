package com.mediflow.report.application.port.out;

import com.mediflow.report.application.dto.response.FinancialArchiveRestoreDTO;
import com.mediflow.report.domain.model.FinancialEvidenceArchive;
import java.util.UUID;

public interface FinancialSourceArchiveStorePort {
    FinancialEvidenceArchive exportArchive(UUID generationId);
    FinancialArchiveRestoreDTO restoreArchive(FinancialEvidenceArchive archive, UUID actorAccountId);
}
