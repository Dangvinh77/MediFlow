package com.mediflow.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Slice test cho {@link LabTestTypeProjectionAdapter} — bảng chiếu local {@code (labId -> labType)}.
 * {@code labType(labId)} rỗng khi chưa có bản chiếu (billing bỏ qua sinh phí LAB lần đó);
 * {@code record(...)} idempotent.
 */
@DataJpaTest
@Import(LabTestTypeProjectionAdapter.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class LabTestTypeProjectionAdapterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private LabTestTypeProjectionAdapter adapter;

    @Test
    void labType_isEmptyUntilRecorded() {
        UUID labId = UUID.randomUUID();
        assertThat(adapter.labType(labId)).isEmpty();

        adapter.record(labId, "CBC");

        assertThat(adapter.labType(labId)).contains("CBC");
    }

    @Test
    void record_sameLabIdTwice_updatesTypeWithoutError() {
        UUID labId = UUID.randomUUID();
        adapter.record(labId, "XQUANG");
        adapter.record(labId, "MRI");

        assertThat(adapter.labType(labId)).contains("MRI");
    }
}
