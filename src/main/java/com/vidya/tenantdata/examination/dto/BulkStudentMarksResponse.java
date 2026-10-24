package com.vidya.tenantdata.examination.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk marks processing summary response")
public class BulkStudentMarksResponse {

    private UUID examSubjectConfigId;
    private int totalProcessed;
    private int createdCount;
    private int updatedCount;
}
