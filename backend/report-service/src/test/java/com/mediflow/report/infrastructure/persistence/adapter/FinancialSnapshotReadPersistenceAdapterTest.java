package com.mediflow.report.infrastructure.persistence.adapter;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.mediflow.report.application.exception.ReportProjectionUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** JDBC statement construction/error boundary, not a substitute for the real PostgreSQL tests. */
@SuppressWarnings({"unchecked","rawtypes"})
class FinancialSnapshotReadPersistenceAdapterTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final FinancialSnapshotReadPersistenceAdapter adapter=new FinancialSnapshotReadPersistenceAdapter(jdbc);
    private final LocalDate day=LocalDate.of(2026,10,10);
    @ParameterizedTest @ValueSource(strings={"timeout","resource"})
    void publication_unavailableDatabase_returnsNotFoundWithoutSqlDetails(String failure) {
        doThrow(failure.equals("timeout")?new QueryTimeoutException("private SQL"):new DataAccessResourceFailureException("private endpoint"))
                .when(jdbc).query(any(PreparedStatementCreator.class),any(RowMapper.class));
        assertThatThrownBy(() -> adapter.publication("VND","Asia/Bangkok"))
                .isInstanceOf(ReportProjectionUnavailableException.class).hasNoCause();
    }
    @ParameterizedTest @ValueSource(strings={"timeout","resource"})
    void read_unavailableDatabase_returnsNotFoundWithoutSqlDetails(String failure) {
        doThrow(failure.equals("timeout")?new QueryTimeoutException("private SQL"):new DataAccessResourceFailureException("private endpoint"))
                .when(jdbc).query(any(PreparedStatementCreator.class),any(RowMapper.class));
        assertThatThrownBy(() -> adapter.read(UUID.randomUUID(),day,day,null))
                .isInstanceOf(ReportProjectionUnavailableException.class).hasNoCause();
    }
    @Test void publication_sqlProgrammingFailure_isNotDisguisedAsMissingCoverage() {
        var failure=new DataIntegrityViolationException("programming error");
        doThrow(failure).when(jdbc).query(any(PreparedStatementCreator.class),any(RowMapper.class));
        assertThatThrownBy(() -> adapter.publication("VND","Asia/Bangkok")).isSameAs(failure);
    }
    @Test void read_unboundedRange_rejectsBeforeDatabase() {
        assertThatThrownBy(() -> adapter.read(UUID.randomUUID(),day,day.plusDays(366),null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.read(UUID.randomUUID(),day,day.minusDays(1),null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }
    private PreparedStatement inspect(ArgumentCaptor<String> sql) throws Exception {
        Connection connection=mock(Connection.class); PreparedStatement statement=mock(PreparedStatement.class);
        when(connection.prepareStatement(sql.capture())).thenReturn(statement);
        doAnswer(invocation -> { ((PreparedStatementCreator)invocation.getArgument(0)).createPreparedStatement(connection); return List.of(); })
                .when(jdbc).query(any(PreparedStatementCreator.class),any(RowMapper.class));
        return statement;
    }
    @Test void publication_exactDimensionsAndVerifiedVersion_fencesOneBoundedQuery() throws Exception {
        var sql=ArgumentCaptor.forClass(String.class); var statement=inspect(sql);
        assertThat(adapter.publication("VND","Asia/Bangkok")).isEmpty();
        assertThat(sql.getValue()).contains("g.status='VERIFIED'","g.projector_version=1","g.snapshot_version=1","g.unresolved_inputs=0");
        verify(statement).setString(1,"VND"); verify(statement).setString(2,"Asia/Bangkok");
        verify(statement).setQueryTimeout(3); verify(statement).setMaxRows(1);
    }
    @ParameterizedTest @ValueSource(strings={"hospital","department"})
    void read_scopePredicateIndexedOrderAndOneExtraRow_areBounded(String scope) throws Exception {
        var sql=ArgumentCaptor.forClass(String.class); var statement=inspect(sql); UUID generation=UUID.randomUUID();
        UUID department=scope.equals("department")?UUID.randomUUID():null;
        adapter.read(generation,day,day.plusDays(1),department);
        assertThat(sql.getValue()).contains(department==null?"department_id IS NULL":"department_id=?")
                .contains("ORDER BY business_date").doesNotContain("COALESCE","SUM(");
        verify(statement).setObject(1,generation);
        if(department!=null) verify(statement).setObject(2,department);
        verify(statement).setQueryTimeout(3); verify(statement).setFetchSize(128); verify(statement).setMaxRows(3);
    }
}
