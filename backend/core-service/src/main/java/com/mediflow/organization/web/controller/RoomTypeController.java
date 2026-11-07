package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.port.in.ManageRoomTypesUseCase;
import com.mediflow.organization.application.port.in.ManageRoomTypesUseCase.NewRoomType;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.web.dto.request.RoomTypeRequest;
import com.mediflow.organization.web.dto.request.UpdateRoomTypeRequest;
import com.mediflow.organization.web.dto.response.RoomTypeResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Room type catalogue ("Danh mục khoa phòng"). Reads for staff who see rooms; writes ADMIN-only. */
@RestController
@RequestMapping("/api/v1/org/room-types")
public class RoomTypeController {

    private final ManageRoomTypesUseCase types;
    private final CorrelationIdProvider correlationIds;

    public RoomTypeController(ManageRoomTypesUseCase types, CorrelationIdProvider correlationIds) {
        this.types = types;
        this.correlationIds = correlationIds;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<List<RoomTypeResponse>>> list() {
        return ok(types.list().stream().map(RoomTypeResponse::from).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoomTypeResponse>> create(@Valid @RequestBody RoomTypeRequest request) {
        RoomTypeResponse type = RoomTypeResponse.from(types.create(new NewRoomType(request.name(),
                request.description(), request.departmentType(), request.singlePerDepartment())));
        return ResponseEntity.created(URI.create("/api/v1/org/room-types/" + type.code()))
                .body(ApiResponse.ok(type, correlationIds.currentOrCreate().toString()));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoomTypeResponse>> rename(@PathVariable String code,
            @Valid @RequestBody UpdateRoomTypeRequest request) {
        return ok(RoomTypeResponse.from(types.rename(code, request.name(), request.description())));
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        types.delete(code);
        return ResponseEntity.noContent().build();
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data, correlationIds.currentOrCreate().toString()));
    }
}
