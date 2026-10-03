package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.RoomStatus;
import com.vidya.tenantdata.timetable.enums.RoomType;

import java.time.Instant;
import java.util.UUID;

public record RoomResponse(
        UUID id,
        String roomCode,
        String name,
        RoomType roomType,
        Integer capacity,
        String building,
        String floor,
        RoomStatus status,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {}
