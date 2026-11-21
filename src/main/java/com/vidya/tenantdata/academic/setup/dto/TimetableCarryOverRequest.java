package com.vidya.tenantdata.academic.setup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** Start this year's timetable from a timetable of an earlier year. */
@Schema(description = "Copy a previous year's timetable into this academic year as a DRAFT")
public record TimetableCarryOverRequest(
        @NotNull @Schema(description = "Timetable of an earlier academic year") UUID sourceTimetableId,
        @Size(max = 200) @Schema(description = "Default: source name + new year code") String name,
        @Schema(description = "Default: the source's bell schedule. With another schedule, periods are matched by sequence number.") UUID bellScheduleId,
        @Schema(description = "Default: start of the academic year") LocalDate effectiveFrom,
        @Schema(description = "Default: end of the academic year") LocalDate effectiveTo,
        @Schema(description = "FROM_ASSIGNMENTS (default): each slot gets the teacher assigned to that subject and section this year, "
                + "preferring last year's teacher. NONE: copy slots without teachers.") TeacherMode teachers
) {
    public enum TeacherMode { FROM_ASSIGNMENTS, NONE }
}
