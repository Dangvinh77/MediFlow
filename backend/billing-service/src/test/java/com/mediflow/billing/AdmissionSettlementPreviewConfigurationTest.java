package com.mediflow.billing;

import com.mediflow.billing.application.port.in.GetAdmissionSettlementPreviewUseCase;
import com.mediflow.billing.application.port.out.AdmissionLedgerReadPort;
import com.mediflow.billing.infrastructure.config.AdmissionSettlementPreviewConfiguration;
import com.mediflow.billing.infrastructure.persistence.adapter.AdmissionLedgerReadAdapter;
import com.mediflow.billing.web.AdmissionSettlementPreviewController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AdmissionSettlementPreviewConfigurationTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(AdmissionSettlementPreviewConfiguration.class, AdmissionSettlementPreviewController.class, AdmissionLedgerReadAdapter.class)
                .withBean(org.springframework.jdbc.core.JdbcTemplate.class, () -> mock(org.springframework.jdbc.core.JdbcTemplate.class))
                .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
    }
    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3}) void preview_bothIndependentGatesRequired(int flags) {
        runner().withPropertyValues("mediflow.billing.ledger.enabled=" + ((flags & 1) != 0),
                "mediflow.billing.settlement-preview.enabled=" + ((flags & 2) != 0)).run(context -> {
            assertThat(context).hasNotFailed();
            if (flags == 3) assertThat(context).hasSingleBean(GetAdmissionSettlementPreviewUseCase.class).hasSingleBean(AdmissionSettlementPreviewController.class).hasSingleBean(AdmissionLedgerReadPort.class);
            else assertThat(context).doesNotHaveBean(GetAdmissionSettlementPreviewUseCase.class).doesNotHaveBean(AdmissionSettlementPreviewController.class).doesNotHaveBean(AdmissionLedgerReadPort.class);
        });
    }
    @Test void preview_defaultHasNoReadEndpoint() {
        runner().run(context -> assertThat(context).doesNotHaveBean(GetAdmissionSettlementPreviewUseCase.class).doesNotHaveBean(AdmissionSettlementPreviewController.class));
    }
}
