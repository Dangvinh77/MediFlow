package com.mediflow.report.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mediflow.report.application.port.in.ReadOutpatientReportUseCase;
import com.mediflow.report.application.port.out.OutpatientReportReadPort;
import com.mediflow.report.web.OutpatientReportController;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ReportOutpatientReadConfigurationTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void readerAndControllerRequireBothGatesAndMvpProfile(int flags) {
        runner().withInitializer(c -> c.getEnvironment().setActiveProfiles("mvp-outpatient"))
                .withPropertyValues("mediflow.features.care-finance-v2=" + ((flags & 1) != 0),
                        "mediflow.report.outpatient-read.enabled=" + ((flags & 2) != 0))
                .run(c -> {
                    assertThat(c).hasNotFailed();
                    if (flags == 3) {
                        assertThat(c).hasSingleBean(ReadOutpatientReportUseCase.class)
                                .hasSingleBean(OutpatientReportController.class);
                    } else {
                        assertThat(c).doesNotHaveBean(ReadOutpatientReportUseCase.class)
                                .doesNotHaveBean(OutpatientReportController.class);
                    }
                });
    }

    @Test
    void bothGatesWithoutProfileDoNotExposeNewReader() {
        runner().withPropertyValues("mediflow.features.care-finance-v2=true",
                        "mediflow.report.outpatient-read.enabled=true")
                .run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(ReadOutpatientReportUseCase.class)
                        .doesNotHaveBean(OutpatientReportController.class));
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(ReportOutpatientReadConfiguration.class, OutpatientReportController.class)
                .withBean(OutpatientReportReadPort.class, () -> mock(OutpatientReportReadPort.class))
                .withBean("reportZoneId", ZoneId.class, () -> ZoneId.of("Asia/Bangkok"));
    }
}
