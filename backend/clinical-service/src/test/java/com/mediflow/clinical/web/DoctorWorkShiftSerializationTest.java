package com.mediflow.clinical.web;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.*;
import java.util.UUID;
import com.fasterxml.jackson.databind.*;
import com.mediflow.clinical.application.dto.response.DoctorWorkShiftDTO;
import com.mediflow.clinical.domain.model.DoctorWorkShift;
import org.junit.jupiter.api.Test;

class DoctorWorkShiftSerializationTest {
    @Test void shiftUsesSameMinutePrecisionWireTimesAsConsultationSession() {
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var id=UUID.randomUUID();
        var json=mapper.valueToTree(new DoctorWorkShiftDTO(id,1,id,id,id,LocalDate.of(2026,11,2),LocalTime.of(8,0),LocalTime.of(11,0),DoctorWorkShift.Status.ACTIVE));
        assertThat(json.get("startTime").asText()).isEqualTo("08:00");
        assertThat(json.get("endTime").asText()).isEqualTo("11:00");
    }
}
