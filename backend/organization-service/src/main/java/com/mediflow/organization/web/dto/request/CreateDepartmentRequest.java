package com.mediflow.organization.web.dto.request;

import com.mediflow.organization.domain.model.DepartmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreateDepartmentRequest {

    @NotBlank(message = "Department name must not be blank")
    @Size(max = 100, message = "Department name must not exceed 100 characters")
    private String departmentName;

    @NotBlank(message = "Abbreviation must not be blank")
    @Size(max = 20, message = "Abbreviation must not exceed 20 characters")
    private String abbreviation;

    @NotNull(message = "Department type must not be null")
    private DepartmentType departmentType;

    @Size(max = 255, message = "Location must not exceed 255 characters")
    private String location;

    public CreateDepartmentRequest() {
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public void setAbbreviation(String abbreviation) {
        this.abbreviation = abbreviation;
    }

    public DepartmentType getDepartmentType() {
        return departmentType;
    }

    public void setDepartmentType(DepartmentType departmentType) {
        this.departmentType = departmentType;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}