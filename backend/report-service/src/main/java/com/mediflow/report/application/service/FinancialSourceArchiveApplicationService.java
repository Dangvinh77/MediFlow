package com.mediflow.report.application.service;

import com.mediflow.report.application.dto.response.FinancialArchiveRestoreDTO;
import com.mediflow.report.application.port.in.ManageFinancialSourceArchivesUseCase;
import com.mediflow.report.application.port.out.FinancialArchiveCodecPort;
import com.mediflow.report.application.port.out.FinancialSourceArchiveStorePort;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialSourceArchiveApplicationService implements ManageFinancialSourceArchivesUseCase {
    private final FinancialSourceArchiveStorePort store;
    private final FinancialArchiveCodecPort codec;
    public FinancialSourceArchiveApplicationService(FinancialSourceArchiveStorePort store, FinancialArchiveCodecPort codec) {
        this.store = store; this.codec = codec;
    }
    @Override @Transactional(transactionManager = "reportTransactionManager", isolation = Isolation.REPEATABLE_READ, readOnly = true, timeout = 15)
    public byte[] exportArchive(UUID generationId) {
        if (generationId == null) throw new IllegalArgumentException("Generation identity required");
        return codec.encode(store.exportArchive(generationId));
    }
    @Override @Transactional(transactionManager = "reportTransactionManager", timeout = 15)
    public FinancialArchiveRestoreDTO restoreArchive(byte[] bytes, UUID actorAccountId) {
        if (actorAccountId == null) throw new IllegalArgumentException("Authenticated archive operator required");
        return store.restoreArchive(codec.decode(bytes), actorAccountId);
    }
}
