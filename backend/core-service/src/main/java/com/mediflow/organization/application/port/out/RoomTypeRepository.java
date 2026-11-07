package com.mediflow.organization.application.port.out;

import com.mediflow.organization.domain.model.RoomTypeDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence port for the room type catalogue. */
public interface RoomTypeRepository {

    List<RoomTypeDefinition> findAll();

    Optional<RoomTypeDefinition> findByCode(String code);

    /** Row lock that serializes rename/delete against room creation of the same type. */
    Optional<RoomTypeDefinition> lockByCode(String code);

    boolean existsByName(String name, String excludedCode);

    void insert(RoomTypeDefinition type);

    void update(RoomTypeDefinition type);

    void delete(String code);

    /** Number of rooms (active or not) per type code; history keeps inactive rooms. */
    Map<String, Long> roomCounts();

    int nextSortOrder();
}
