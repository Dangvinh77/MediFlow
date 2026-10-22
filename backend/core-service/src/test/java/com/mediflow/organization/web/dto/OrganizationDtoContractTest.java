package com.mediflow.organization.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.organization.domain.model.DepartmentType;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.web.dto.request.ChangeStaffDepartmentRequest;
import com.mediflow.organization.web.dto.request.CreateDepartmentRequest;
import com.mediflow.organization.web.dto.request.CreateStaffRequest;
import com.mediflow.organization.web.dto.request.UpdateDepartmentRequest;
import com.mediflow.organization.web.dto.request.UpdateStaffRequest;
import com.mediflow.organization.web.dto.response.DepartmentResponse;
import com.mediflow.organization.web.dto.response.StaffResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationDtoContractTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID departmentId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void requestRecordsKeepExistingEnglishJsonFieldNames() throws Exception {
        assertFields(new CreateDepartmentRequest("Clinical", "CLIN", DepartmentType.CLINICAL, "A"),
                "departmentName", "abbreviation", "departmentType", "location", "profile");
        assertFields(new UpdateDepartmentRequest("Clinical", DepartmentType.CLINICAL,
                        "A", id, true),
                "departmentName", "departmentType", "location", "departmentHeadId", "active", "profile");
        assertFields(new CreateStaffRequest("Doctor", departmentId, JobTitle.DOCTOR,
                        "Surgery", "LIC-1", "0123456789", "doctor@example.com", "012345678901"),
                "fullName", "departmentId", "jobTitle", "specialization", "licenseNumber",
                "phoneNumber", "email", "identityNumber", "profile");
        assertFields(new UpdateStaffRequest("Doctor", JobTitle.DOCTOR, "Surgery", "LIC-1",
                        "0123456789", "doctor@example.com"),
                "fullName", "jobTitle", "specialization", "licenseNumber", "phoneNumber", "email", "profile");
        assertFields(new ChangeStaffDepartmentRequest(departmentId), "newDepartmentId");
    }

    @Test
    void updateDepartmentRequiresDepartmentHeadFieldButAcceptsExplicitNull() throws Exception {
        UpdateDepartmentRequest request = mapper.readValue("""
                {"departmentName":"Clinical","departmentType":"CLINICAL",
                 "location":"A","departmentHeadId":null,"active":true}
                """, UpdateDepartmentRequest.class);

        assertThat(request.departmentHeadId()).isNull();
        assertThatThrownBy(() -> mapper.readValue("""
                {"departmentName":"Clinical","departmentType":"CLINICAL",
                 "location":"A","active":true}
                """, UpdateDepartmentRequest.class))
                .isInstanceOf(JsonMappingException.class);
    }

    @Test
    void responseRecordsKeepExistingEnglishJsonFieldNamesAndEnumValues() throws Exception {
        assertFields(new DepartmentResponse(id, "Clinical", "CLIN", DepartmentType.CLINICAL,
                        departmentId, "A", true, timestamp, timestamp),
                "departmentId", "departmentName", "abbreviation", "departmentType",
                "departmentHeadId", "location", "active", "createdAt", "updatedAt", "description", "phoneNumber", "email");
        JsonNode department = mapper.valueToTree(new DepartmentResponse(id, "Clinical", "CLIN",
                DepartmentType.CLINICAL, departmentId, "A", true, timestamp, timestamp));
        assertThat(department.get("departmentType").asText()).isEqualTo("CLINICAL");

        assertFields(new StaffResponse(id, "Doctor", departmentId, JobTitle.DOCTOR, "Surgery",
                        "LIC-1", "0123456789", "doctor@example.com", true, timestamp, timestamp, "012345678901"),
                "staffId", "fullName", "departmentId", "jobTitle", "specialization",
                "licenseNumber", "phoneNumber", "email", "active", "createdAt", "updatedAt", "identityNumber", "dateOfBirth", "gender", "address", "hireDate", "qualification");
        JsonNode staff = mapper.valueToTree(new StaffResponse(id, "Doctor", departmentId,
                JobTitle.DOCTOR, "Surgery", "LIC-1", "0123456789", "doctor@example.com",
                true, timestamp, timestamp, "012345678901"));
        assertThat(staff.get("jobTitle").asText()).isEqualTo("DOCTOR");
    }

    private void assertFields(Object value, String... expected) throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(value));
        Set<String> fields = new HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(expected);
    }
}
