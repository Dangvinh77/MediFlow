package com.mediflow.pharmacy.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent;
import com.mediflow.pharmacy.application.event.carefinance.PrescriptionCareEvent.EventType;

class PrescriptionCareEventWriterAdapterTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PrescriptionCareEventCodec codec = new PrescriptionCareEventCodec(new ObjectMapper().findAndRegisterModules());
    private final PrescriptionCareEventWriterAdapter writer = new PrescriptionCareEventWriterAdapter(jdbc, codec);

    @Test
    void creationStoresHeldBytesAndVerifiesImmutableReadback() throws Exception {
        var event = fixture(EventType.CREATED);
        readback(event, false, new String(codec.encode(event), StandardCharsets.UTF_8));
        writer.storeHeld(event);
        verify(jdbc).update(contains("false, 1, ?"), eq(event.eventId()), eq("prescription.created"),
                eq(event.payload().prescriptionId()), eq(new String(codec.encode(event), StandardCharsets.UTF_8)), eq(0));
        verify(jdbc, never()).queryForObject(anyString(), eq(Boolean.class), any(Object.class));
    }

    @Test
    void missingCreationRejectsTerminalBeforeInsert() throws Exception {
        var event = fixture(EventType.FILLED);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(event.payload().prescriptionId()))).thenReturn(false);
        assertThatThrownBy(() -> writer.storeHeld(event)).hasMessageContaining("creation");
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void changedPayloadOrUnexpectedlyEnabledRow_isNotAcceptedAsIdempotent() throws Exception {
        var event = fixture(EventType.CREATED);
        readback(event, false, "different bytes");
        assertThatThrownBy(() -> writer.storeHeld(event)).hasMessageContaining("conflict");
        readback(event, true, new String(codec.encode(event), StandardCharsets.UTF_8));
        assertThatThrownBy(() -> writer.storeHeld(event)).hasMessageContaining("conflict");
    }

    @Test
    void eventCollisionWithNoMatchingSemanticRow_isRejected() throws Exception {
        var event = fixture(EventType.CREATED);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object.class), anyInt()))
                .thenReturn(List.of());
        assertThatThrownBy(() -> writer.storeHeld(event)).hasMessageContaining("conflict");
    }

    @Test
    void findHeld_readsTypedImmutableBytesAndRequiresVersionHoldAndRoutingKey() throws Exception {
        var event = fixture(EventType.FILLED);
        var row = mock(ResultSet.class);
        when(row.getString("payload")).thenReturn(new String(codec.encode(event), StandardCharsets.UTF_8));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object.class), anyString()))
                .thenAnswer(call -> List.of(call.<RowMapper<Object>>getArgument(1).mapRow(row, 0)));
        assertThat(writer.findHeld(event.payload().prescriptionId(), EventType.FILLED)).contains(event);
        verify(jdbc).query(contains("care_contract_version = 1 AND NOT delivery_enabled"),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(event.payload().prescriptionId()), eq("prescription.filled"));
    }

    private void readback(PrescriptionCareEvent event, boolean enabled, String bytes) throws Exception {
        var result = mock(ResultSet.class);
        when(result.getObject("event_id", UUID.class)).thenReturn(event.eventId());
        when(result.getString("payload")).thenReturn(bytes);
        when(result.getBoolean("delivery_enabled")).thenReturn(enabled);
        when(result.getShort("care_contract_version")).thenReturn((short) 1);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object.class), anyInt()))
                .thenAnswer(invocation -> List.of(invocation.<RowMapper<Object>>getArgument(1).mapRow(result, 0)));
    }

    private PrescriptionCareEvent fixture(EventType type) throws Exception {
        try (var stream = getClass().getResourceAsStream("/contracts/care-finance-v1/" + type.routingKey() + ".v1.json")) {
            if (stream == null) throw new IllegalStateException("Fixture missing");
            return codec.decode(type.routingKey(), stream.readAllBytes());
        }
    }
}
