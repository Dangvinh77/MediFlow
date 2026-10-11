package com.mediflow.report.application.port.out;

import com.mediflow.report.domain.model.FinancialEvidenceArchive;

public interface FinancialArchiveCodecPort {
    byte[] encode(FinancialEvidenceArchive archive);
    FinancialEvidenceArchive decode(byte[] bytes);
}
