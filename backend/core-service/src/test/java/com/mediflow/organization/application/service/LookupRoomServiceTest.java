package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.RoomLookupDTO;
import com.mediflow.organization.application.port.out.RoomRepository;
import com.mediflow.organization.domain.model.Room;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LookupRoomServiceTest {

    private final RoomRepository repository = mock(RoomRepository.class);
    private final LookupRoomService service = new LookupRoomService(repository);

    @Test
    void existingActiveRoom_returnsIdentityProjection() {
        UUID roomId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        Room room = Room.reconstitute(roomId, departmentId, "OR-01", "OPERATING_ROOM",
                true, Instant.now(), Instant.now());
        when(repository.findById(roomId)).thenReturn(Optional.of(room));

        assertThat(service.lookup(roomId))
                .isEqualTo(new RoomLookupDTO(true, true, roomId, departmentId,
                        "OR-01", "OPERATING_ROOM"));
    }

    @Test
    void missingRoom_echoesRequestedIdAsConfirmedAbsence() {
        UUID roomId = UUID.randomUUID();
        when(repository.findById(roomId)).thenReturn(Optional.empty());

        assertThat(service.lookup(roomId)).isEqualTo(RoomLookupDTO.missing(roomId));
    }

    @Test
    void inactiveRoom_remainsDistinguishableFromMissing() {
        UUID roomId = UUID.randomUUID();
        Room room = Room.reconstitute(roomId, UUID.randomUUID(), "OR-02", "OPERATING_ROOM",
                false, Instant.now(), Instant.now());
        when(repository.findById(roomId)).thenReturn(Optional.of(room));

        assertThat(service.lookup(roomId).exists()).isTrue();
        assertThat(service.lookup(roomId).active()).isFalse();
    }
}
