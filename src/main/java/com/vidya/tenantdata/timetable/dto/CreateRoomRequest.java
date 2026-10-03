package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.RoomType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
        @NotBlank(message = "Room name is required")
        @Size(max = 100, message = "Room name cannot exceed 100 characters")
        String name,

        @NotNull(message = "Room type is required")
        RoomType roomType,

        @Min(value = 0, message = "Capacity must be non-negative")
        Integer capacity,

        @Size(max = 100, message = "Building name cannot exceed 100 characters")
        String building,

        @Size(max = 50, message = "Floor cannot exceed 50 characters")
        String floor
) {}
