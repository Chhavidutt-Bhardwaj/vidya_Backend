package com.vidya.tenantdata.timetable.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.util.List;

@Schema(description = "Daily breakdown of room timetable occupancy")
public record DayRoomOccupancyResponse(
        @Schema(description = "Day of the week")
        DayOfWeek dayOfWeek,

        @Schema(description = "Number of teaching periods occupied in this room", example = "4")
        int occupiedSlotsCount,

        @Schema(description = "Total available teaching periods on this day", example = "6")
        int availableTeachingSlotsCount,

        @Schema(description = "Room utilisation percentage for this day", example = "66.67")
        double utilizationPercentage,

        @Schema(description = "Grid slots occupied on this day")
        List<TimetableGridSlotResponse> slots
) {}
