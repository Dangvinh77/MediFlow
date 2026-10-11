package com.mediflow.report.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import com.mediflow.report.domain.model.OperationalContribution.Metric;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;

class OperationalSnapshotReadPersistenceAdapterTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final OperationalSnapshotReadPersistenceAdapter store = new OperationalSnapshotReadPersistenceAdapter(jdbc);

    @Test void read_hospitalUsesOneBoundedStatementWithRealNullScope() throws Exception {
        UUID generation = UUID.randomUUID();
        LocalDate from = LocalDate.of(2028, 1, 1);
        when(jdbc.query(any(PreparedStatementCreator.class), any(RowMapper.class))).thenReturn(List.of());
        store.read(generation, from, from.plusDays(365), null, Set.of(Metric.ADMISSIONS, Metric.DISCHARGES));
        var creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc).query(creator.capture(), any(RowMapper.class));
        verifyNoMoreInteractions(jdbc);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        assertThat(creator.getValue().createPreparedStatement(connection)).isSameAs(statement);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sql.capture());
        assertThat(sql.getValue()).contains("department_id IS NULL", "metric_date BETWEEN ? AND ?", "metric_type IN (?,?)")
                .doesNotContain("IS NOT DISTINCT FROM", "JOIN", " OFFSET ");
        verify(statement).setObject(1, generation);
        verify(statement).setQueryTimeout(3);
        verify(statement).setFetchSize(128);
        verify(statement).setMaxRows(733);
    }

    @Test void read_departmentUsesEqualityAndBoundParameters() throws Exception {
        when(jdbc.query(any(PreparedStatementCreator.class), any(RowMapper.class))).thenReturn(List.of());
        UUID department = UUID.randomUUID();
        store.read(UUID.randomUUID(), LocalDate.of(2028, 2, 29), LocalDate.of(2028, 2, 29), department, Set.of(Metric.ADMISSIONS));
        var creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc).query(creator.capture(), any(RowMapper.class));
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        creator.getValue().createPreparedStatement(connection);
        verify(connection).prepareStatement(contains("department_id = ?"));
        verify(statement).setObject(2, department);
        verify(statement).setMaxRows(2);
    }

    @Test void read_invalidScopeFailsBeforeSql() {
        LocalDate date = LocalDate.of(2028, 1, 1);
        for (LocalDate to : List.of(date.minusDays(1), date.plusDays(366))) {
            assertThatThrownBy(() -> store.read(UUID.randomUUID(), date, to, null, Set.of(Metric.ADMISSIONS)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> store.read(UUID.randomUUID(), date, date, null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test void publicationAndRead_timeoutAreUnavailableNeverSuccessfulZero() {
        when(jdbc.query(any(PreparedStatementCreator.class), any(RowMapper.class)))
                .thenThrow(new QueryTimeoutException("private storage detail"));
        assertThatThrownBy(() -> store.publication("DAILY")).isInstanceOf(ReportProjectionUnavailableException.class)
                .hasMessageNotContaining("private");
        LocalDate date = LocalDate.of(2028, 2, 29);
        assertThatThrownBy(() -> store.read(UUID.randomUUID(), date, date, null, Set.of(Metric.ADMISSIONS)))
                .isInstanceOf(ReportProjectionUnavailableException.class).hasMessageNotContaining("private");
    }

    @Test void read_driverCancellationOrConnectionLossIsUnavailableNotAnEmptyResult() {
        when(jdbc.query(any(PreparedStatementCreator.class), any(RowMapper.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private connection or SQLSTATE 57014"));
        LocalDate date = LocalDate.of(2028, 2, 29);
        assertThatThrownBy(() -> store.read(UUID.randomUUID(), date, date, null, Set.of(Metric.ADMISSIONS)))
                .isInstanceOf(ReportProjectionUnavailableException.class).hasMessageNotContaining("private");
        assertThatThrownBy(() -> store.publication("DAILY"))
                .isInstanceOf(ReportProjectionUnavailableException.class).hasMessageNotContaining("private");
    }

    @Test void publication_oneStatementAndOneRowWithinSameTimeoutBudget() throws Exception {
        when(jdbc.query(any(PreparedStatementCreator.class), any(RowMapper.class))).thenReturn(List.of());
        assertThat(store.publication("SURGERY")).isEmpty();
        var creator = ArgumentCaptor.forClass(PreparedStatementCreator.class);
        verify(jdbc).query(creator.capture(), any(RowMapper.class));
        verifyNoMoreInteractions(jdbc);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        creator.getValue().createPreparedStatement(connection);
        verify(statement).setString(1, "SURGERY");
        verify(statement).setQueryTimeout(3);
        verify(statement).setMaxRows(1);
    }

    @Test void read_sqlProgrammingErrorIsNotMisclassifiedAsMissingCoverage() {
        var failure = new org.springframework.jdbc.BadSqlGrammarException("query", "private SQL",
                new java.sql.SQLException("bad SQL", "42601"));
        when(jdbc.query(any(PreparedStatementCreator.class), any(RowMapper.class))).thenThrow(failure);
        LocalDate date = LocalDate.of(2028, 2, 29);
        assertThatThrownBy(() -> store.read(UUID.randomUUID(), date, date, null, Set.of(Metric.ADMISSIONS)))
                .isSameAs(failure);
    }
}
