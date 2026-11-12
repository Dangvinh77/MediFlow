package com.mediflow.clinical.infrastructure.persistence.adapter;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mediflow.clinical.application.port.out.VitalSignRepositoryPort;
import com.mediflow.clinical.application.service.ConditionalOnClinicIntake;
import com.mediflow.clinical.domain.model.VitalSignObservation;

@Component
@ConditionalOnClinicIntake
public class VitalSignPersistenceAdapter implements VitalSignRepositoryPort {
    private final JdbcTemplate jdbc;

    public VitalSignPersistenceAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override @Transactional
    public VitalSignObservation insert(VitalSignObservation o) {
        jdbc.update("""
                INSERT INTO vital_sign_observation(observation_id, registration_id, patient_id, department_id, observed_at,
                    recorded_by_account_id, recorded_by_staff_id, pulse_bpm, systolic_mmhg, diastolic_mmhg, temperature_c,
                    respiratory_rate, spo2_percent, weight_kg, not_measured_reason)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, o.observationId(), o.registrationId(), o.patientId(), o.departmentId(), Timestamp.from(o.observedAt()),
                o.recordedByAccountId(), o.recordedByStaffId(), o.pulseBpm(), o.systolicMmhg(), o.diastolicMmhg(), o.temperatureC(),
                o.respiratoryRate(), o.spo2Percent(), o.weightKg(), o.notMeasuredReason());
        return o;
    }

    @Override @Transactional(readOnly = true)
    public List<VitalSignObservation> listByRegistration(UUID registrationId) {
        return jdbc.query("SELECT * FROM vital_sign_observation WHERE registration_id = ? ORDER BY observed_at DESC, observation_id",
                this::map, registrationId);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.intValue();
    }

    private VitalSignObservation map(ResultSet rs, int row) throws SQLException {
        BigDecimal temperature = rs.getBigDecimal("temperature_c");
        BigDecimal weight = rs.getBigDecimal("weight_kg");
        return new VitalSignObservation(rs.getObject("observation_id", UUID.class), rs.getObject("registration_id", UUID.class),
                rs.getObject("patient_id", UUID.class), rs.getObject("department_id", UUID.class), rs.getTimestamp("observed_at").toInstant(),
                rs.getObject("recorded_by_account_id", UUID.class), rs.getObject("recorded_by_staff_id", UUID.class), integer(rs, "pulse_bpm"),
                integer(rs, "systolic_mmhg"), integer(rs, "diastolic_mmhg"), temperature, integer(rs, "respiratory_rate"),
                integer(rs, "spo2_percent"), weight, rs.getString("not_measured_reason"));
    }
}
