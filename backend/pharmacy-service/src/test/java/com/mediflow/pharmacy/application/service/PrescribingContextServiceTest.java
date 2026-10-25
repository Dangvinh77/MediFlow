package com.mediflow.pharmacy.application.service;

import com.mediflow.pharmacy.application.dto.command.ActorIdentity;
import com.mediflow.pharmacy.application.port.out.OutpatientPrescriptionContextPort;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrescribingContextServiceTest {
    private final UUID record = UUID.randomUUID(), patient = UUID.randomUUID(), doctor = UUID.randomUUID(), department = UUID.randomUUID(), episode = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-25T08:00:00Z");
    private final OutpatientPrescriptionContextPort port = mock(OutpatientPrescriptionContextPort.class);
    private PrescribingContextService service(List<String> codes) { return new PrescribingContextService(port, codes, Clock.fixed(now, ZoneOffset.UTC)); }
    private OutpatientPrescriptionContextPort.Observation source(Instant observed) {
        return new OutpatientPrescriptionContextPort.Observation(true,record,patient,doctor,department,"OUTPATIENT_VISIT",episode,"OPEN",null,observed);
    }
    @Test void usesExactClinicalEpisodeAndConfiguredPricePolicy() {
        when(port.findRecord(record,"trace")).thenReturn(source(now));
        var result = service(List.of("LOCAL-CATALOG")).get(record,new ActorIdentity(UUID.randomUUID(),doctor,"DOCTOR"),"trace");
        assertThat(result.careEpisodeId()).isEqualTo(episode).isNotEqualTo(record);
        assertThat(result.patientId()).isEqualTo(patient);
        assertThat(result.priceCodes()).containsExactly("LOCAL-CATALOG");
    }
    @Test void deniesOtherDoctorAndNonPrescriber() {
        when(port.findRecord(record,"trace")).thenReturn(source(now));
        assertThatThrownBy(() -> service(List.of("CODE")).get(record,new ActorIdentity(UUID.randomUUID(),UUID.randomUUID(),"DOCTOR"),"trace")).hasMessageContaining("bác sĩ khác");
        clearInvocations(port);
        assertThatThrownBy(() -> service(List.of("CODE")).get(record,new ActorIdentity(UUID.randomUUID(),doctor,"PHARMACIST"),"trace")).hasMessageContaining("chưa được xác nhận");
        verifyNoInteractions(port);
    }
    @Test void neverInventsPriceOrUsesStaleSource() {
        when(port.findRecord(record,"trace")).thenReturn(source(now));
        var admin = new ActorIdentity(UUID.randomUUID(),null,"ADMIN");
        assertThatThrownBy(() -> service(List.of()).get(record,admin,"trace")).hasMessageContaining("Chưa cấu hình");
        when(port.findRecord(record,"trace")).thenReturn(source(now.minusSeconds(31)));
        assertThatThrownBy(() -> service(List.of("CODE")).get(record,admin,"trace")).isInstanceOf(com.mediflow.pharmacy.application.exception.PharmacyUpstreamUnavailableException.class);
    }
}
