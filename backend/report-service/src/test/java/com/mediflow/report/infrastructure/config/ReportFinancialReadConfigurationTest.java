package com.mediflow.report.infrastructure.config;

import com.mediflow.report.application.port.in.ReadFinancialSnapshotUseCase;
import com.mediflow.report.application.port.out.FinancialSnapshotReadPort;
import com.mediflow.report.web.FinancialSnapshotReportController;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ReportFinancialReadConfigurationTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner()
            .withUserConfiguration(ReportFinancialReadConfiguration.class,FinancialSnapshotReportController.class)
            .withBean(FinancialSnapshotReadPort.class,() -> mock(FinancialSnapshotReadPort.class));
    @Test void context_defaultFalse_exposesNeitherReaderNorController() {
        runner.run(context -> { assertThat(context).doesNotHaveBean(ReadFinancialSnapshotUseCase.class); assertThat(context).doesNotHaveBean(FinancialSnapshotReportController.class); });
    }
    @Test void context_onlyGlobalGate_hasNoFinanceReader() { runner.withPropertyValues("mediflow.features.care-finance-v2=true").run(context -> assertThat(context).doesNotHaveBean(ReadFinancialSnapshotUseCase.class)); }
    @Test void context_onlyFinanceGate_hasNoController() { runner.withPropertyValues("mediflow.report.financial-read.enabled=true").run(context -> assertThat(context).doesNotHaveBean(FinancialSnapshotReportController.class)); }
    @Test void context_bothGates_registersBothWithDefaults() {
        runner.withPropertyValues("mediflow.features.care-finance-v2=true","mediflow.report.financial-read.enabled=true")
                .run(context -> { assertThat(context).hasSingleBean(ReadFinancialSnapshotUseCase.class); assertThat(context).hasSingleBean(FinancialSnapshotReportController.class); });
    }
    @Test void context_invalidCurrency_failsStartupRatherThanMixedMoney() {
        runner.withPropertyValues("mediflow.features.care-finance-v2=true","mediflow.report.financial-read.enabled=true","mediflow.report.financial-read.currency=vn")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void context_invalidZone_failsStartup() {
        runner.withPropertyValues("mediflow.features.care-finance-v2=true","mediflow.report.financial-read.enabled=true","mediflow.report.zone-id=invalid")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void context_providedClock_doesNotCreateDuplicateGlobalClock() {
        runner.withBean(Clock.class,() -> Clock.fixed(Instant.parse("2027-01-02T00:00:00Z"),ZoneOffset.UTC))
                .withPropertyValues("mediflow.features.care-finance-v2=true","mediflow.report.financial-read.enabled=true")
                .run(context -> { assertThat(context).hasSingleBean(Clock.class); assertThat(context).hasSingleBean(ReadFinancialSnapshotUseCase.class); });
    }
}
