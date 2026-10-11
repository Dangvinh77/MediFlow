package com.mediflow.report.domain.model;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class FinancialReportPublicationTest {
    private final LocalDate from=LocalDate.of(2026,10,1),to=LocalDate.of(2026,10,10);
    private final Instant observed=Instant.parse("2026-10-10T17:00:00Z");
    private FinancialReportPublication publication(Instant seen,Instant reconciled,Instant published,String zone,String currency,String reference) {
        return new FinancialReportPublication(UUID.randomUUID(),from,to,currency,zone,seen,reconciled,published,reference);
    }
    @Test void publication_completedDayAtZoneBoundary_coversOnlyExactCurrencyZoneAndPeriod() {
        var value=publication(observed,observed,observed,"Asia/Bangkok","VND","coverage");
        assertThat(value.covers(from,to,"VND","Asia/Bangkok")).isTrue();
        assertThat(value.covers(from,to,"USD","Asia/Bangkok")).isFalse();
        assertThat(value.covers(from,to,"VND","UTC")).isFalse();
        assertThat(value.covers(from.minusDays(1),to,"VND","Asia/Bangkok")).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"open-day","reconcile-before-observe","publish-before-reconcile","bad-zone","bad-currency","missing-reference"})
    void publication_incompleteOrContradictoryCoverage_isRejected(String mode) {
        assertThatThrownBy(() -> publication(mode.equals("open-day")?observed.minusNanos(1):observed,
                mode.equals("reconcile-before-observe")?observed.minusSeconds(1):observed,
                mode.equals("publish-before-reconcile")?observed.minusSeconds(1):observed,
                mode.equals("bad-zone")?"invalid":"Asia/Bangkok",mode.equals("bad-currency")?"bad":"VND",
                mode.equals("missing-reference")?" ":"accepted"))
                .isInstanceOf(RuntimeException.class);
    }
}
