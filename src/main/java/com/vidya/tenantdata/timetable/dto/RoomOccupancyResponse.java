package com.vidya.tenantdata.timetable.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Room utilisation and occupancy breakdown across timetable schedule")
public record RoomOccupancyResponse(
        @Schema(description = "Timetable ID")
        UUID timetableId,

        @Schema(description = "Room ID")
        UUID roomId,

        @Schema(description = "Room unique identifier code", example = "ROM-101")
        String roomCode,

        @Schema(description = "Room display name", example = "Physics Lab")
        String roomName,

        @Schema(description = "Total available teaching slots across selected days", example = "36")
        int totalAvailableTeachingSlots,

        @Schema(description = "Total occupied teaching slots across selected days", example = "24")
        int totalOccupiedSlots,

        @Schema(description = "Overall room utilisation percentage", example = "66.67")
        double utilizationPercentage,

        @Schema(description = "Daily breakdown of occupancy")
        List<DayRoomOccupancyResponse> dailyOccupancy
) {}
