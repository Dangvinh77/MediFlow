package com.mediflow.organization.infrastructure.persistence.adapter;

import com.mediflow.common.exception.DuplicateResourceException;
import com.mediflow.organization.application.port.out.SurgeryAuthorityRepository;
import com.mediflow.organization.domain.model.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/** Atomic compare-and-set writes; concurrent first grants cannot overwrite each other. */
@Repository
public class SurgeryAuthorityRepositoryAdapter implements SurgeryAuthorityRepository {
    private final JdbcTemplate jdbc;

    public SurgeryAuthorityRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<OperatingRoom> findRoom(UUID id) {
        return jdbc.query("SELECT * FROM operating_room WHERE room_id=?", (row, index) ->
                new OperatingRoom(row.getObject("room_id", UUID.class), row.getString("room_code"),
                        row.getString("room_name"), row.getObject("department_id", UUID.class),
                        row.getBoolean("is_active"), row.getLong("revision"),
                        row.getTimestamp("updated_at").toInstant()), id).stream().findFirst();
    }

    @Override
    public void saveRoom(OperatingRoom room, long expected) {
        try {
            int rows;
            if (expected == 0) {
                rows = jdbc.update("""
                        INSERT INTO operating_room(room_id,room_code,room_name,department_id,is_active,revision,updated_at)
                        VALUES(?,?,?,?,?,?,?)
                        """, room.roomId(), room.roomCode(), room.roomName(), room.departmentId(),
                        room.active(), room.revision(), Timestamp.from(room.updatedAt()));
            } else {
                rows = jdbc.update("""
                        UPDATE operating_room SET room_code=?,room_name=?,department_id=?,is_active=?,
                        revision=?,updated_at=? WHERE room_id=? AND revision=?
                        """, room.roomCode(), room.roomName(), room.departmentId(), room.active(),
                        room.revision(), Timestamp.from(room.updatedAt()), room.roomId(), expected);
            }
            if (rows != 1) throw conflict();
        } catch (DuplicateKeyException duplicate) {
            throw new DuplicateResourceException("ORG_AUTHORITY_CONFLICT",
                    "Room code/identity already exists or revision changed");
        }
    }

    @Override
    public Optional<SurgicalCapability> findCapability(UUID id, SurgicalTeamRole role) {
        return jdbc.query("SELECT * FROM surgical_capability WHERE staff_id=? AND team_role=?", (row, index) ->
                new SurgicalCapability(id, role, row.getObject("department_id", UUID.class),
                        row.getBoolean("is_active"), row.getTimestamp("valid_from").toInstant(),
                        row.getTimestamp("valid_until").toInstant(), row.getLong("revision"),
                        row.getTimestamp("updated_at").toInstant()), id, role.name()).stream().findFirst();
    }

    @Override
    public void saveCapability(SurgicalCapability capability, long expected) {
        try {
            int rows;
            if (expected == 0) {
                rows = jdbc.update("""
                        INSERT INTO surgical_capability(staff_id,team_role,department_id,is_active,
                        valid_from,valid_until,revision,updated_at) VALUES(?,?,?,?,?,?,?,?)
                        """, capability.staffId(), capability.teamRole().name(), capability.departmentId(),
                        capability.active(), Timestamp.from(capability.validFrom()),
                        Timestamp.from(capability.validUntil()), capability.revision(),
                        Timestamp.from(capability.updatedAt()));
            } else {
                rows = jdbc.update("""
                        UPDATE surgical_capability SET department_id=?,is_active=?,valid_from=?,valid_until=?,
                        revision=?,updated_at=? WHERE staff_id=? AND team_role=? AND revision=?
                        """, capability.departmentId(), capability.active(), Timestamp.from(capability.validFrom()),
                        Timestamp.from(capability.validUntil()), capability.revision(),
                        Timestamp.from(capability.updatedAt()), capability.staffId(), capability.teamRole().name(), expected);
            }
            if (rows != 1) throw conflict();
        } catch (DuplicateKeyException duplicate) {
            throw conflict();
        }
    }

    private static DuplicateResourceException conflict() {
        return new DuplicateResourceException("ORG_AUTHORITY_CONFLICT", "Authority revision changed");
    }
}
