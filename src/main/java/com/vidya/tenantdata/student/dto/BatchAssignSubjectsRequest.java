package com.vidya.tenantdata.student.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to assign multiple subjects to a student enrollment in batch")
public class BatchAssignSubjectsRequest {

    @NotEmpty(message = "Subject IDs list cannot be empty")
    @Schema(description = "List of Subject UUIDs to assign")
    private List<UUID> subjectIds;

    @Schema(description = "Effective start date (defaults to today)", example = "2026-06-01")
    private LocalDate effectiveFrom;

    @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
    @Schema(description = "Remarks for batch subject assignment")
    private String remarks;
}
