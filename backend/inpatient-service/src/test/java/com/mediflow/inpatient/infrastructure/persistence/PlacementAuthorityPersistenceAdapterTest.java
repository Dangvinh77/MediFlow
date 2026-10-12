package com.mediflow.inpatient.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mediflow.inpatient.infrastructure.persistence.adapter.PlacementAuthorityPersistenceAdapter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class PlacementAuthorityPersistenceAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void malformedPlacementRowIsReportedAsLookupUnavailable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID admissionId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(admissionId)))
                .thenThrow(new IllegalArgumentException("incomplete placement"));

        var adapter = new PlacementAuthorityPersistenceAdapter(jdbc);

        assertThatThrownBy(() -> adapter.findByAdmissionId(admissionId))
                .isInstanceOf(DataRetrievalFailureException.class)
                .hasMessageContaining("placement authority");
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingRequiredPlacementFieldIsReportedAsLookupUnavailable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UUID admissionId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(RowMapper.class), eq(admissionId)))
                .thenThrow(new NullPointerException("patientId"));

        var adapter = new PlacementAuthorityPersistenceAdapter(jdbc);

        assertThatThrownBy(() -> adapter.findByAdmissionId(admissionId))
                .isInstanceOf(DataRetrievalFailureException.class)
                .hasMessageContaining("placement authority");
    }
}
