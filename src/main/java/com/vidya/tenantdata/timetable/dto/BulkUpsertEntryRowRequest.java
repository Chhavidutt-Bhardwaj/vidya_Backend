package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.TimetableEntryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.util.UUID;

@Schema(description = "Single entry row for section bulk upsert")
public record BulkUpsertEntryRowRequest(
        @NotNull(message = "Period ID is required")
        @Schema(description = "Bell schedule period ID")
        UUID periodId,

        @NotNull(message = "Day of week is required")
        @Schema(description = "Day of the week", example = "MONDAY")
        DayOfWeek dayOfWeek,

        @Schema(description = "Class subject assignment ID")
        UUID classSubjectAssignmentId,

        @Schema(description = "Direct Subject ID")
        UUID subjectId,

        @Schema(description = "Direct Teacher (Staff) ID")
        UUID teacherId,

        @Schema(description = "Teacher subject assignment ID")
        UUID teacherAssignmentId,

        @Schema(description = "Assigned Room ID")
        UUID roomId,

        @Schema(description = "Timetable entry type", example = "REGULAR")
        TimetableEntryType entryType
) {}
