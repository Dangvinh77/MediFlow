package com.mediflow.organization.web.controller;

import com.mediflow.organization.application.port.in.ChangeStaffDepartmentUseCase;
import com.mediflow.organization.application.port.in.CreateStaffUseCase;
import com.mediflow.organization.application.port.in.GetStaffUseCase;
import com.mediflow.organization.domain.model.JobTitle;
import com.mediflow.organization.domain.model.Staff;
import com.mediflow.organization.web.dto.request.ChangeStaffDepartmentRequest;
import com.mediflow.organization.web.dto.request.CreateStaffRequest;
import com.mediflow.organization.web.dto.response.CreateStaffResponse;
import com.mediflow.organization.web.dto.response.StaffResponse;
import com.mediflow.organization.web.dto.response.StaffExistsResponse;
import com.mediflow.organization.application.port.in.UpdateStaffUseCase;
import com.mediflow.organization.web.dto.request.UpdateStaffRequest;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/staff")
public class StaffController {

        private final CreateStaffUseCase createStaffUseCase;
        private final ChangeStaffDepartmentUseCase changeStaffDepartmentUseCase;
        private final GetStaffUseCase getStaffUseCase;
        private final UpdateStaffUseCase updateStaffUseCase;

        public StaffController(
                        CreateStaffUseCase createStaffUseCase,
                        ChangeStaffDepartmentUseCase changeStaffDepartmentUseCase,
                        GetStaffUseCase getStaffUseCase,
                        UpdateStaffUseCase updateStaffUseCase) {
                this.createStaffUseCase = createStaffUseCase;
                this.changeStaffDepartmentUseCase = changeStaffDepartmentUseCase;
                this.getStaffUseCase = getStaffUseCase;
                this.updateStaffUseCase = updateStaffUseCase;
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
                                .created(URI.create("/api/v1/org/staff/" + staffId))
                                .body(response);
        }

        @PutMapping("/{staffId}/department")
        public ResponseEntity<Void> changeDepartment(
                        @PathVariable UUID staffId,
                        @Valid @RequestBody ChangeStaffDepartmentRequest request) {

                changeStaffDepartmentUseCase.execute(
                                staffId,
                                request.getNewDepartmentId());

                return ResponseEntity.noContent().build();
        }

        @GetMapping
        public ResponseEntity<List<StaffResponse>> getAllStaff(
                        @RequestParam(name = "departmentId", required = false) UUID departmentId,
                        @RequestParam(name = "jobTitle", required = false) JobTitle jobTitle,
                        @RequestParam(name = "page", defaultValue = "0") int page,
                        @RequestParam(name = "size", defaultValue = "20") int size) {
                if (page < 0 || size <= 0) {
                        throw new IllegalArgumentException("page must be >= 0 and size must be > 0");
                }

                List<Staff> staffList = (departmentId != null)
                                ? getStaffUseCase.getStaffByDepartmentId(departmentId)
                                : getStaffUseCase.getAllStaff();

                List<Staff> filteredStaff = staffList.stream()
                                .filter(staff -> jobTitle == null || staff.getJobTitle() == jobTitle)
                                .skip((long) page * size)
                                .limit(size)
                                .toList();

                List<StaffResponse> response = filteredStaff.stream()
                                .map(StaffResponse::from)
                                .toList();

                return ResponseEntity.ok(response);
        }

        @PutMapping("/{staffId}")
        public ResponseEntity<StaffResponse> updateStaff(
                        @PathVariable UUID staffId,
                        @Valid @RequestBody UpdateStaffRequest request) {
                Staff staff = updateStaffUseCase.execute(
                                staffId,
                                request.getFullName(),
                                request.getJobTitle(),
                                request.getSpecialization(),
                                request.getLicenseNumber(),
                                request.getPhoneNumber(),
                                request.getEmail());

                return ResponseEntity.ok(StaffResponse.from(staff));
        }

        @GetMapping("/{id}")
        public ResponseEntity<StaffResponse> getStaffById(@PathVariable UUID id) {
                Staff staff = getStaffUseCase.getStaffById(id);
                return ResponseEntity.ok(StaffResponse.from(staff));
        }

        @GetMapping("/{id}/exists")
        public ResponseEntity<StaffExistsResponse> staffExists(@PathVariable UUID id) {
                if (!getStaffUseCase.existsById(id)) {
                        return ResponseEntity.ok(new StaffExistsResponse(false, null));
                }

                Staff staff = getStaffUseCase.getStaffById(id);
                return ResponseEntity.ok(new StaffExistsResponse(true, staff.getDepartmentId()));
        }

}
