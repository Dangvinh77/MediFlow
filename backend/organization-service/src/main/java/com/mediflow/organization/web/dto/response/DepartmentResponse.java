package com.mediflow.organization.web.dto.response;

import com.mediflow.organization.domain.model.Department;
import com.mediflow.organization.domain.model.DepartmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentResponse {
    
    private UUID departmentId;
    private String departmentName;
    private String abbreviation;
    private DepartmentType departmentType;
    private UUID departmentHeadId;
    private String location;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public static DepartmentResponse from(Department domain) {
    return DepartmentResponse.builder()
            .departmentId(domain.getDepartmentId())
            .departmentName(domain.getDepartmentName())
            .abbreviation(domain.getAbbreviation())
            .departmentType(domain.getDepartmentType())
            .departmentHeadId(domain.getDepartmentHeadId())
            .location(domain.getLocation())
            .active(domain.isActive())
            .createdAt(domain.getCreatedAt())
            .updatedAt(domain.getUpdatedAt())
            .build();
}
}

