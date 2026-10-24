package com.vidya.tenantdata.reportcard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to publish generated report cards")
public class PublishReportCardsRequest {

    @NotNull(message = "termId is required")
    @Schema(description = "Exam Term UUID")
    private UUID termId;

    @NotNull(message = "classId is required")
    @Schema(description = "Academic Class UUID")
    private UUID classId;

    @Schema(description = "Optional section filter UUID")
    private UUID sectionId;
}
