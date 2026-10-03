package com.vidya.tenantdata.timetable.dto;

import com.vidya.tenantdata.timetable.enums.TimetableEntryType;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.util.UUID;

public record UpdateTimetableEntryRequest(
        @NotNull(message = "Period ID is required")
        UUID periodId,

        @NotNull(message = "Day of week is required")
        DayOfWeek dayOfWeek,

        @NotNull(message = "Academic class ID is required")
        UUID academicClassId,

        UUID sectionId,
        UUID subjectId,
        UUID teacherAssignmentId,
        UUID teacherId,
        UUID classSubjectAssignmentId,
        UUID roomId,
        TimetableEntryType entryType
) {}
