package com.vidya.tenantdata.timetable.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(description = "Teacher weekly workload and teaching periods summary")
public record TeacherLoadResponse(
        @Schema(description = "Timetable ID")
        UUID timetableId,

        @Schema(description = "Teacher ID")
        UUID teacherId,

        @Schema(description = "Teacher full name", example = "John Doe")
        String teacherName,

        @Schema(description = "Teacher staff code", example = "STF-101")
        String staffCode,

        @Schema(description = "Total teaching periods assigned across the selected week", example = "24")
        int totalWeeklyTeachingPeriods,

        @Schema(description = "Total teaching slots available in bell schedule across selected week", example = "40")
        int totalWeeklyAvailableTeachingSlots,

        @Schema(description = "Daily workload breakdown")
        List<DayTeacherLoadResponse> dailyLoad
) {}
