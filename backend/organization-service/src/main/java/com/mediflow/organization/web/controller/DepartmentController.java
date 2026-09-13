package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.port.in.CreateDepartmentUseCase;
import com.mediflow.organization.web.dto.request.CreateDepartmentRequest;
import com.mediflow.organization.web.dto.response.CreateDepartmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/departments")
public class DepartmentController {

    private final CreateDepartmentUseCase createDepartmentUseCase;

    public DepartmentController(
            CreateDepartmentUseCase createDepartmentUseCase
    ) {
        this.createDepartmentUseCase = createDepartmentUseCase;
    }

    @PostMapping
    public ResponseEntity<CreateDepartmentResponse> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request
    ) {

        UUID departmentId =
                createDepartmentUseCase.execute(
                        request.getDepartmentName(),
                        request.getAbbreviation(),
                        request.getDepartmentType(),
                        request.getLocation()
                );

        CreateDepartmentResponse response =
                new CreateDepartmentResponse(departmentId);

        return ResponseEntity
                .created(URI.create("/departments/" + departmentId))
                .body(response);
    }
}