package com.mediflow.inpatient.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mediflow.inpatient.application.port.out.AdmissionAuthorityRepositoryPort;
import com.mediflow.inpatient.domain.model.AdmissionAuthoritySnapshot;
import com.mediflow.inpatient.domain.model.enums.AdmissionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LookupAdmissionAuthorityServiceTest {
    private static final UUID ID=UUID.fromString("05000000-0000-0000-0000-000000000001");
    private static final UUID PATIENT=UUID.fromString("06000000-0000-0000-0000-000000000001");
    private static final UUID DEPT=UUID.fromString("03000000-0000-0000-0000-000000000001");
    private static final UUID RECORD=UUID.fromString("07000000-0000-0000-0000-000000000001");
    private static final Instant NOW=Instant.parse("2026-10-05T02:00:00Z");
    private final AdmissionAuthorityRepositoryPort admissions=mock(AdmissionAuthorityRepositoryPort.class);
    private final LookupAdmissionAuthorityService service=new LookupAdmissionAuthorityService(admissions,Clock.fixed(NOW,ZoneOffset.UTC));

    @ParameterizedTest
    @EnumSource(AdmissionStatus.class)
    void lookup_onlyAdmittedWithinMedicalWindow_isEligible(AdmissionStatus status) {
        when(admissions.findById(ID)).thenReturn(Optional.of(snapshot(status,1,null)));
        assertThat(service.lookup(ID).eligible()).isEqualTo(status==AdmissionStatus.ADMITTED);
    }

    @Test void lookup_inconsistentHistoricalAdmittedWithDischargeTime_isIneligible() {
        when(admissions.findById(ID)).thenReturn(Optional.of(snapshot(AdmissionStatus.ADMITTED,2,NOW)));
        assertThat(service.lookup(ID).eligible()).isFalse();
    }

    @Test void lookup_actualProducerDTOs_matchCanonicalFixtures() throws Exception {
        when(admissions.findById(ID)).thenReturn(Optional.of(snapshot(AdmissionStatus.ADMITTED,1,null)));
        fixture("admission.active.json");
        when(admissions.findById(ID)).thenReturn(Optional.of(snapshot(AdmissionStatus.MEDICALLY_DISCHARGED,2,NOW)));
        fixture("admission.discharged.json");
        when(admissions.findById(ID)).thenReturn(Optional.empty());
        fixture("admission.missing.json");
    }

    @Test void lookup_outage_isNotMissing() {
        when(admissions.findById(ID)).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(()->service.lookup(ID)).isInstanceOf(IllegalStateException.class);
    }

    private static AdmissionAuthoritySnapshot snapshot(AdmissionStatus status,long revision,Instant discharge) {
        return new AdmissionAuthoritySnapshot(ID,PATIENT,DEPT,RECORD,status,revision,discharge,null,null);
    }
    private void fixture(String name) throws Exception {
        var mapper=JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        try(var bytes=getClass().getResourceAsStream("/contracts/admission-authority-v1/"+name)) {
            assertThat(bytes).isNotNull();
            assertThat((JsonNode)mapper.valueToTree(service.lookup(ID))).isEqualTo(mapper.readTree(bytes).get("data"));
        }
    }
}
