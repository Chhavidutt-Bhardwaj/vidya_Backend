package com.vidya.tenantdata.timetable.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request body for bulk upserting timetable entries of a section")
public record BulkUpsertSectionEntriesRequest(
        @NotNull(message = "Entries list cannot be null")
        @Size(max = 300, message = "Bulk upsert cannot exceed 300 entries per request")
        @Valid
        @Schema(description = "List of entry rows to upsert (max 300)")
        List<BulkUpsertEntryRowRequest> entries,

        @Schema(description = "When true, existing section entries not in the payload will be soft-deleted", defaultValue = "false")
        Boolean replaceAll
) {
    public BulkUpsertSectionEntriesRequest {
        if (replaceAll == null) {
            replaceAll = false;
        }
        if (entries == null) {
            entries = List.of();
        }
    }
}
