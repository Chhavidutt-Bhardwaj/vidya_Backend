package com.vidya.tenantdata.timetable.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Response summary for bulk upsert operations on timetable entries")
public record BulkUpsertEntriesResponse(
        @Schema(description = "Number of new timetable entries created", example = "5")
        int createdCount,

        @Schema(description = "Number of existing timetable entries updated", example = "3")
        int updatedCount,

        @Schema(description = "Number of timetable entries soft-deleted (via replaceAll)", example = "2")
        int deletedCount,

        @Schema(description = "Total resulting active entries for the section")
        List<TimetableEntryResponse> entries
) {}
