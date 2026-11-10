package com.mediflow.clinical.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import com.mediflow.clinical.application.dto.response.ReturnSourceDTO;
import com.mediflow.clinical.application.service.OpenVisitApplicationService;
import com.mediflow.clinical.infrastructure.correlation.ThreadLocalCorrelationIdProvider;
import com.mediflow.clinical.infrastructure.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = OpenVisitController.class, properties = "mediflow.clinical.scheduling.enabled=true")
@Import({OpenVisitControllerTest.MethodSecurity.class, ThreadLocalCorrelationIdProvider.class, CorrelationIdFilter.class})
class OpenVisitControllerTest {
    private static final String PATIENT_PATH = "/api/v2/patients/" + UUID.randomUUID() + "/open-visits";
    private static final String DOCTOR_PATH = "/api/v2/doctors/me/open-visits";

    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}

    @Autowired MockMvc mvc;
    @MockBean OpenVisitApplicationService visits;

    @Test void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get(PATIENT_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(DOCTOR_PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(visits);
    }

    @ParameterizedTest @ValueSource(strings = {"CASHIER", "PATIENT", "PHARMACIST", "LAB_TECH"})
    void patientListIsForCareStaff(String role) throws Exception {
        mvc.perform(get(PATIENT_PATH).with(user("u").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(visits);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "NURSE", "CASHIER", "PATIENT"})
    void ownListIsForDoctors(String role) throws Exception {
        mvc.perform(get(DOCTOR_PATH).with(user("u").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(visits);
    }

    private static final String RETURN_PATH = "/api/v2/patients/" + UUID.randomUUID() + "/return-sources";

    @ParameterizedTest @ValueSource(strings = {"DOCTOR", "CASHIER", "PATIENT", "PHARMACIST", "LAB_TECH"})
    void returnSourcesAreForRegistrationStaff(String role) throws Exception {
        mvc.perform(get(RETURN_PATH).with(user("u").roles(role))).andExpect(status().isForbidden());
        verifyNoInteractions(visits);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "NURSE"})
    void registrationStaffSeeReturnSources(String role) throws Exception {
        when(visits.returnSources(any())).thenReturn(List.of(new ReturnSourceDTO(UUID.randomUUID(), "COMPLETED",
                java.time.LocalDate.of(2026, 11, 1), UUID.randomUUID())));
        mvc.perform(get(RETURN_PATH).with(user("u").roles(role))).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].status").value("COMPLETED"));
    }

    @Test void nurseSeesThePatientList() throws Exception {
        mvc.perform(get(PATIENT_PATH).with(user("n").roles("NURSE"))).andExpect(status().isOk());
    }

    @Test void doctorSeesOwnList() throws Exception {
        when(visits.forCurrentDoctor()).thenReturn(List.of());
        mvc.perform(get(DOCTOR_PATH).with(user("d").roles("DOCTOR"))).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    }
}
