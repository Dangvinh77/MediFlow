package com.mediflow.organization.web.controller;

import com.mediflow.common.api.ApiResponse;
import com.mediflow.organization.application.dto.response.RoomLookupDTO;
import com.mediflow.organization.application.port.in.LookupRoomUseCase;
import com.mediflow.organization.application.port.out.CorrelationIdProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/org/rooms")
public class RoomController {

    private final LookupRoomUseCase lookupRoomUseCase;
    private final CorrelationIdProvider correlationIds;

    public RoomController(LookupRoomUseCase lookupRoomUseCase,
                          CorrelationIdProvider correlationIds) {
        this.lookupRoomUseCase = lookupRoomUseCase;
        this.correlationIds = correlationIds;
    }

    @GetMapping("/{id}/lookup")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_SERVICE')")
    public ResponseEntity<ApiResponse<RoomLookupDTO>> lookupRoom(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(
                lookupRoomUseCase.lookup(id), correlationIds.currentOrCreate().toString()));
    }
}
