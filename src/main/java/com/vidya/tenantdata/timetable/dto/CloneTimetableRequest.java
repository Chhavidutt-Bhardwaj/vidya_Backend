package com.vidya.tenantdata.timetable.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Request body for cloning an existing timetable")
public record CloneTimetableRequest(
        @Size(max = 200, message = "Timetable name must not exceed 200 characters")
        @Schema(description = "Optional custom name for the cloned timetable. If omitted, generates '<Source Name> (Copy)'", example = "Spring 2026 Revised")
        String name,

        @Schema(description = "Effective start date for the cloned timetable")
        LocalDate effectiveFrom,

        @Schema(description = "Effective end date for the cloned timetable")
        LocalDate effectiveTo,

        @Schema(description = "When true, whole clone fails if any entry is invalid. When false (default), invalid entries are skipped and reported.", defaultValue = "false")
        Boolean strict
) {
    public CloneTimetableRequest {
        if (strict == null) {
            strict = false;
        }
    }
}
