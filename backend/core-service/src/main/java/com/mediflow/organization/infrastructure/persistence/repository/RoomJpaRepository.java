package com.mediflow.organization.infrastructure.persistence.repository;

import com.mediflow.organization.infrastructure.persistence.entity.RoomEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomJpaRepository extends JpaRepository<RoomEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomEntity r where r.roomId = :roomId")
    Optional<RoomEntity> lockById(@Param("roomId") UUID roomId);

    @Query("""
            select r from RoomEntity r
            where (:departmentId is null or r.departmentId = :departmentId)
              and (:roomType is null or r.roomType = :roomType)
              and (:activeOnly = false or r.active = true)
            order by r.zone, r.roomNumber, r.roomId
            """)
    List<RoomEntity> search(@Param("departmentId") UUID departmentId,
                            @Param("roomType") String roomType,
                            @Param("activeOnly") boolean activeOnly);

    @Query("""
            select count(r) > 0 from RoomEntity r
            where lower(r.zone) = lower(:zone) and lower(r.roomNumber) = lower(:roomNumber)
              and (:excludedRoomId is null or r.roomId <> :excludedRoomId)
            """)
    boolean existsNumberInZone(@Param("zone") String zone,
                               @Param("roomNumber") String roomNumber,
                               @Param("excludedRoomId") UUID excludedRoomId);

    @Query("""
            select r from RoomEntity r
            where r.departmentId = :departmentId and r.active = true
              and (:excludedRoomId is null or r.roomId <> :excludedRoomId)
            """)
    List<RoomEntity> findActiveByDepartment(@Param("departmentId") UUID departmentId,
                                            @Param("excludedRoomId") UUID excludedRoomId);
}
