package com.mediflow.report.application.port.in;

import com.mediflow.report.application.dto.response.FinancialArchiveRestoreDTO;
import java.util.UUID;

public interface ManageFinancialSourceArchivesUseCase {
    byte[] exportArchive(UUID generationId);
    FinancialArchiveRestoreDTO restoreArchive(byte[] archiveBytes, UUID actorAccountId);
}
