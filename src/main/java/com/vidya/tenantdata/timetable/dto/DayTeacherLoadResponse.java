package com.vidya.tenantdata.timetable.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.util.List;

@Schema(description = "Daily breakdown of teacher teaching load")
public record DayTeacherLoadResponse(
        @Schema(description = "Day of the week")
        DayOfWeek dayOfWeek,

        @Schema(description = "Number of teaching periods assigned to teacher on this day", example = "5")
        int teachingPeriodsCount,

        @Schema(description = "Number of free teaching periods available for this teacher on this day", example = "2")
        int freeTeachingSlotsCount,

        @Schema(description = "Total teaching periods defined in bell schedule on this day", example = "7")
        int totalTeachingSlotsCount,

        @Schema(description = "Assigned slots for teacher on this day")
        List<TimetableGridSlotResponse> assignedSlots
) {}
