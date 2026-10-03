package com.vidya.tenantdata.timetable.mapper;

import com.vidya.tenantdata.timetable.dto.RoomResponse;
import com.vidya.tenantdata.timetable.entity.Room;
import org.springframework.stereotype.Component;

@Component
public class RoomMapper {

    public RoomResponse toResponse(Room room) {
        if (room == null) {
            return null;
        }
        return new RoomResponse(
                room.getId(),
                room.getRoomCode(),
                room.getName(),
                room.getRoomType(),
                room.getCapacity(),
                room.getBuilding(),
                room.getFloor(),
                room.getStatus(),
                room.getCreatedAt(),
                room.getUpdatedAt(),
                room.getVersion()
        );
    }
}
