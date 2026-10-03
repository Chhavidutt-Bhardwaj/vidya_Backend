package com.vidya.tenantdata.timetable.repository;

import com.vidya.tenantdata.timetable.entity.Room;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.RoomType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

    @Query("SELECT r FROM Room r WHERE r.id = :id AND r.deletedAt IS NULL")
    Optional<Room> findByIdAndNotDeleted(@Param("id") UUID id);

    @Query("SELECT r FROM Room r WHERE r.roomCode = :roomCode AND r.deletedAt IS NULL")
    Optional<Room> findByRoomCodeAndNotDeleted(@Param("roomCode") String roomCode);

    @Query("SELECT r FROM Room r WHERE r.deletedAt IS NULL " +
           "AND (:roomType IS NULL OR r.roomType = :roomType) " +
           "AND (:status IS NULL OR r.status = :status) " +
           "AND (:building IS NULL OR LOWER(r.building) LIKE LOWER(CONCAT('%', :building, '%'))) " +
           "AND (:query IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(r.roomCode) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Room> searchRooms(
            @Param("roomType") RoomType roomType,
            @Param("status") RoomStatus status,
            @Param("building") String building,
            @Param("query") String query,
            Pageable pageable
    );

    @Query("SELECT COUNT(r) > 0 FROM Room r WHERE LOWER(r.name) = LOWER(:name) " +
           "AND r.deletedAt IS NULL AND (:excludeId IS NULL OR r.id <> :excludeId)")
    boolean existsByNameIgnoreCaseAndNotDeleted(@Param("name") String name, @Param("excludeId") UUID excludeId);

    boolean existsByRoomCode(String roomCode);
}
