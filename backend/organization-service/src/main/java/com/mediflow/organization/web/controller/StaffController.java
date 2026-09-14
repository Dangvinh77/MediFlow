package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.port.in.ChangeStaffDepartmentUseCase;
import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.in.GetStaffUseCase;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.web.dto.request.ChangeStaffDepartmentRequest;
import com.mediflow.organization.web.dto.request.CreateStaffRequest;
import com.mediflow.organization.web.dto.response.CreateStaffResponse;
import com.mediflow.organization.web.dto.response.StaffResponse;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/staff")
public class StaffController {

        private final CreateStaffUseCase createStaffUseCase;
        private final ChangeStaffDepartmentUseCase changeStaffDepartmentUseCase;
        private final GetStaffUseCase getStaffUseCase;

        public StaffController(
                        CreateStaffUseCase createStaffUseCase,
                        ChangeStaffDepartmentUseCase changeStaffDepartmentUseCase,
                        GetStaffUseCase getStaffUseCase) {
                this.createStaffUseCase = createStaffUseCase;
                this.changeStaffDepartmentUseCase = changeStaffDepartmentUseCase;
                this.getStaffUseCase = getStaffUseCase;
        }

        @PostMapping
        public ResponseEntity<CreateStaffResponse> createStaff(
                        @Valid @RequestBody CreateStaffRequest request) {

                UUID staffId = createStaffUseCase.execute(
                                request.getFullName(),
                                request.getDepartmentId(),
                                request.getJobTitle(),
                                request.getSpecialization(),
                                request.getLicenseNumber(),
                                request.getPhoneNumber(),
                                request.getEmail());

                CreateStaffResponse response = new CreateStaffResponse(staffId);

                return ResponseEntity
                                .created(URI.create("/staff/" + staffId))
                                .body(response);
        }

        @PatchMapping("/{staffId}/department")
        public ResponseEntity<Void> changeDepartment(
                        @PathVariable UUID staffId,
                        @Valid @RequestBody ChangeStaffDepartmentRequest request) {

                changeStaffDepartmentUseCase.execute(
                                staffId,
                                request.getNewDepartmentId());

                return ResponseEntity.noContent().build();
        }

        @GetMapping
        public ResponseEntity<List<StaffResponse>> getAllStaff(@RequestParam(required = false) UUID departmentId) {
                List<Staff> staffList = (departmentId != null)
                                ? getStaffUseCase.getStaffByDepartmentId(departmentId)
                                : getStaffUseCase.getAllStaff();

                List<StaffResponse> response = staffList.stream()
                                .map(StaffResponse::from)
                                .toList();

                return ResponseEntity.ok(response);
        }

        @GetMapping("/{id}")
        public ResponseEntity<StaffResponse> getStaffById(@PathVariable UUID id) {
                Staff staff = getStaffUseCase.getStaffById(id);
                return ResponseEntity.ok(StaffResponse.from(staff));
        }
}