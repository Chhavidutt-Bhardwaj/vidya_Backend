package com.vidya.tenantdata.timetable.service;

import com.vidya.tenantdata.timetable.dto.CreateRoomRequest;
import com.vidya.tenantdata.timetable.dto.RoomResponse;
import com.vidya.tenantdata.timetable.dto.UpdateRoomRequest;
import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.RoomType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import com.vidya.tenantdata.timetable.dto.UpdateRoomStatusRequest;

public interface RoomService {
    RoomResponse createRoom(CreateRoomRequest request);
    RoomResponse updateRoom(UUID id, UpdateRoomRequest request);
    RoomResponse updateRoomStatus(UUID id, UpdateRoomStatusRequest request);
    RoomResponse getRoomById(UUID id);
    RoomResponse getRoomByCode(String roomCode);
    Page<RoomResponse> searchRooms(String search, RoomType roomType, RoomStatus status, String building, Pageable pageable);
    void deleteRoom(UUID id);
}
