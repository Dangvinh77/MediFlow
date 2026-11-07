package com.mediflow.organization.application.service;

import com.mediflow.organization.application.dto.response.RoomLookupDTO;
import com.mediflow.organization.application.port.out.RoomRepository;
import com.mediflow.organization.domain.model.Room;
import com.mediflow.organization.domain.model.RoomType;
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
        Room room = Room.reconstitute(roomId, departmentId, RoomType.CONSULTATION, "101", "Phòng khám 1",
                "Khu A", "Tầng 1", true, Instant.now(), Instant.now());
        when(repository.findById(roomId)).thenReturn(Optional.of(room));

        assertThat(service.lookup(roomId))
                .isEqualTo(new RoomLookupDTO(true, true, roomId, departmentId,
                        "Phòng khám 1", "CONSULTATION"));
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
        Room room = Room.reconstitute(roomId, UUID.randomUUID(), RoomType.LABORATORY, "102", "Phòng xét nghiệm",
                "Khu B", "Tầng 1", false, Instant.now(), Instant.now());
        when(repository.findById(roomId)).thenReturn(Optional.of(room));

        assertThat(service.lookup(roomId).exists()).isTrue();
        assertThat(service.lookup(roomId).active()).isFalse();
    }
}
