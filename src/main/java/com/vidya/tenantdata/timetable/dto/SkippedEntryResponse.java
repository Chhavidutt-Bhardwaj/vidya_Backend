package com.vidya.tenantdata.timetable.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Details of an entry skipped during timetable cloning")
public record SkippedEntryResponse(
        @Schema(description = "Source entry ID")
        UUID sourceEntryId,

        @Schema(description = "Source entry code", example = "TTE-2026-001")
        String sourceEntryCode,

        @Schema(description = "Reason why the entry could not be cloned", example = "Teacher assignment is no longer active")
        String reason
) {}
