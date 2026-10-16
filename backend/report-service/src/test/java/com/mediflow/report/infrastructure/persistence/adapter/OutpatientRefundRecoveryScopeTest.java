package com.mediflow.report.infrastructure.persistence.adapter;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.env.MockEnvironment;

/** SQL binding check only; PostgreSQL migration/behavior acceptance remains a separate test. */
class OutpatientRefundRecoveryScopeTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pendingAndDeferUseProfileScopeIncludingCompatibility(boolean mvp) {
        var jdbc = mock(JdbcTemplate.class);
        var environment = new MockEnvironment();
        environment.setActiveProfiles(mvp ? "mvp-outpatient" : "default");
        var adapter = new CashRefundPersistenceAdapter(jdbc, new ObjectMapper(), environment);
        UUID original = UUID.randomUUID();
        adapter.pending(original, 20);
        verify(jdbc).query(contains("care_episode_type='OUTPATIENT_VISIT'"), any(RowMapper.class),
                eq(original), eq(mvp), eq(20));
        adapter.defer(original);
        verify(jdbc).update(contains("care_episode_type='OUTPATIENT_VISIT'"), eq(original), eq(mvp));
    }
}
