package com.vidya.tenantdata.examination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk marks submission request for an exam subject")
public class BulkStudentMarksRequest {

    @NotNull(message = "examSubjectConfigId is required")
    @Schema(description = "Exam subject configuration UUID")
    private UUID examSubjectConfigId;

    @Valid
    @NotEmpty(message = "Entries list cannot be empty")
    @Schema(description = "List of student mark records to upsert")
    private List<SubmitStudentMarkRequest> entries;
}
