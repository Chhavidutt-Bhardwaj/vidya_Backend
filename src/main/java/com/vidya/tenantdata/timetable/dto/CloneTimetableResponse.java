package com.vidya.tenantdata.timetable.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Response summary for cloning a timetable")
public record CloneTimetableResponse(
        @Schema(description = "Cloned timetable details")
        TimetableResponse timetable,

        @Schema(description = "Number of entries successfully cloned into new timetable", example = "35")
        int clonedEntriesCount,

        @Schema(description = "Number of entries skipped due to validity issues", example = "2")
        int skippedEntriesCount,

        @Schema(description = "List of skipped entries with explanations")
        List<SkippedEntryResponse> skippedEntries
) {}
