package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.dto.response.RoomLookupDTO;
import com.mediflow.organization.application.port.in.LookupRoomUseCase;
import com.mediflow.organization.application.port.in.ManageRoomsUseCase;
import com.mediflow.organization.application.port.in.ManageRoomsUseCase.RoomDetails;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import com.mediflow.organization.web.dto.request.RoomRequest;
import com.mediflow.organization.web.dto.request.RoomStatusRequest;
import com.mediflow.organization.web.dto.request.UpdateRoomRequest;
import com.mediflow.organization.web.dto.response.RoomResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/rooms")
public class RoomController {

    private final LookupRoomUseCase lookupRoomUseCase;
    private final ManageRoomsUseCase rooms;
    private final CorrelationIdProvider correlationIds;

    public RoomController(LookupRoomUseCase lookupRoomUseCase,
                          ManageRoomsUseCase rooms,
                          CorrelationIdProvider correlationIds) {
        this.lookupRoomUseCase = lookupRoomUseCase;
        this.rooms = rooms;
        this.correlationIds = correlationIds;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> list(
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) String roomType,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return ok(rooms.list(departmentId, roomType, activeOnly).stream().map(RoomResponse::from).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR','NURSE')")
    public ResponseEntity<ApiResponse<RoomResponse>> get(@PathVariable UUID id) {
        return ok(RoomResponse.from(rooms.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoomResponse>> create(@Valid @RequestBody RoomRequest request) {
        RoomResponse room = RoomResponse.from(rooms.create(
                new RoomDetails(request.departmentId(), request.roomType(), request.placement())));
        return ResponseEntity.created(URI.create("/api/v1/org/rooms/" + room.roomId()))
                .body(ApiResponse.ok(room, correlationIds.currentOrCreate().toString()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoomResponse>> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateRoomRequest request) {
        return ok(RoomResponse.from(rooms.update(id, request.placement())));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RoomResponse>> changeStatus(@PathVariable UUID id,
            @Valid @RequestBody RoomStatusRequest request) {
        return ok(RoomResponse.from(rooms.changeStatus(id, request.active())));
    }

    @GetMapping("/{id}/lookup")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE')")
    public ResponseEntity<ApiResponse<RoomLookupDTO>> lookupRoom(@PathVariable UUID id) {
        return ok(lookupRoomUseCase.lookup(id));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.ok(data, correlationIds.currentOrCreate().toString()));
    }
}
