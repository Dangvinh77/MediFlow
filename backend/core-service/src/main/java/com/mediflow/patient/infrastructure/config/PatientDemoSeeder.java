package com.mediflow.patient.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediflow.common.api.PageQuery;
import com.mediflow.patient.application.dto.request.CreatePatientRequest;
import com.mediflow.patient.application.dto.response.PatientDTO;
import com.mediflow.patient.application.port.in.CreatePatientUseCase;
import com.mediflow.patient.application.port.in.GetPatientUseCase;
import com.mediflow.patient.domain.model.Gender;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reads/writes only Patient's own store; returns bare IDs to Core composition. */
public class PatientDemoSeeder {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final GetPatientUseCase patients;
    private final CreatePatientUseCase createPatient;

    public PatientDemoSeeder(JdbcTemplate jdbc, TransactionTemplate transaction,
            GetPatientUseCase patients, CreatePatientUseCase createPatient) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.patients = patients;
        this.createPatient = createPatient;
    }

    public Map<String, UUID> seed(JsonNode data) {
        return transaction.execute(status -> {
            jdbc.execute("select pg_advisory_xact_lock(78324513)");
            Map<String, UUID> ids = new HashMap<>();
            List<PatientDTO> existing = allPatients();
            for (JsonNode row : data.required("patients")) {
                String key = row.required("key").asText();
                var saved = jdbc.query("select entity_id from local_demo_seed_receipt where seed_key=?",
                        (rs, index) -> rs.getObject(1, UUID.class), key);
                UUID id;
                if (!saved.isEmpty()) {
                    id = saved.getFirst();
                } else {
                    id = existing.stream().filter(p -> p.soCmnd().equals(row.required("soCmnd").asText()))
                            .findFirst().map(PatientDTO::maBenhNhan)
                            .orElseGet(() -> createPatient.create(new CreatePatientRequest(
                                    text(row, "hoTen"), LocalDate.parse(text(row, "ngaySinh")),
                                    Gender.valueOf(text(row, "gioiTinh")), text(row, "soCmnd"),
                                    text(row, "diaChi"), text(row, "soDienThoai"), text(row, "email"),
                                    text(row, "bhytSo"))).maBenhNhan());
                    jdbc.update("insert into local_demo_seed_receipt(seed_key,entity_id) values (?,?)", key, id);
                }
                ids.put(key, id);
            }
            return ids;
        });
    }

    private List<PatientDTO> allPatients() {
        List<PatientDTO> result = new ArrayList<>();
        for (int page = 0; ; page++) {
            var batch = patients.search(null, PageQuery.of(page, 100));
            result.addAll(batch.content());
            if (page + 1 >= batch.totalPages()) return result;
        }
    }

    private static String text(JsonNode row, String field) {
        return row.required(field).isNull() ? null : row.required(field).asText();
    }
}
