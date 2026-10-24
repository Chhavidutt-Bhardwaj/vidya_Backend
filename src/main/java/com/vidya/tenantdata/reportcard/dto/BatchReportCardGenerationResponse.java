package com.vidya.tenantdata.reportcard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response summary for batch report cards generation")
public class BatchReportCardGenerationResponse {

    private UUID examTermId;
    private UUID academicClassId;
    private UUID sectionId;
    private int totalStudentsProcessed;
    private int generatedCount;
    private int updatedCount;
    private Instant completedAt;
}
