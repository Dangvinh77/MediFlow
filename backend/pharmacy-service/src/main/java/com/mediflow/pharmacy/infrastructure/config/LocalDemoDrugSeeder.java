package com.mediflow.pharmacy.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.mediflow.pharmacy.application.dto.request.CreateDrugRequest;
import com.mediflow.pharmacy.application.port.in.ManageDrugUseCase;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Local demo setup through the normal drug use case, with durable one-time receipts. */
public class LocalDemoDrugSeeder {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ManageDrugUseCase drugs;
    private final Clock clock;

    public LocalDemoDrugSeeder(JdbcTemplate jdbc, TransactionTemplate transaction,
            ManageDrugUseCase drugs, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.drugs = drugs;
        this.clock = clock;
    }

    /** Serializes concurrent startup and commits each catalogue batch with its receipts. */
    public int seed(JsonNode rows) {
        if (!rows.isArray()) {
            throw new IllegalArgumentException("Demo drug catalogue must be an array");
        }
        return transaction.execute(status -> {
            jdbc.execute("SELECT pg_advisory_xact_lock(716290031)");
            var keys = new HashSet<String>();
            int inserted = 0;
            for (JsonNode row : rows) {
                String key = row.required("key").asText();
                if (key.isBlank() || key.length() > 100 || !keys.add(key)) {
                    throw new IllegalArgumentException("Invalid or duplicate demo drug key");
                }
                if (Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM local_demo_drug_seed_receipt WHERE seed_key=?)",
                        Boolean.class, key))) {
                    continue;
                }
                var drug = drugs.create(new CreateDrugRequest(
                        row.required("drugName").asText(), row.required("activeIngredient").asText(),
                        row.required("unit").asText(), row.required("price").decimalValue(),
                        row.required("stockQuantity").intValue(), LocalDate.now(clock).plusYears(3),
                        "MediFlow Demo", row.required("lowStockThreshold").intValue()));
                jdbc.update("INSERT INTO local_demo_drug_seed_receipt(seed_key,drug_id) VALUES (?,?)",
                        key, drug.drugId());
                inserted++;
            }
            return inserted;
        });
    }
}
